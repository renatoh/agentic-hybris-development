package com.custom.productservices.cart.impl;

import de.hybris.platform.commerceservices.order.CommerceCartService;
import de.hybris.platform.commerceservices.service.data.CommerceCartParameter;
import de.hybris.platform.core.enums.GroupType;
import de.hybris.platform.core.model.order.AbstractOrderEntryModel;
import de.hybris.platform.core.model.order.CartEntryModel;
import de.hybris.platform.core.model.order.CartModel;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.core.order.EntryGroup;
import de.hybris.platform.order.CartService;
import de.hybris.platform.order.EntryGroupService;
import de.hybris.platform.servicelayer.model.ModelService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.custom.core.model.ServiceProductModel;
import com.custom.productservices.cart.CartServiceSelectionService;
import com.custom.productservices.exceptions.CartServiceSelectionException;
import com.custom.productservices.service.ProductServiceLookupService;
import com.custom.productservices.service.ServiceEntryGroupService;


/**
 * NET-8943 &sect;5.4. Service entries are created through the platform's {@link CartService} and recalculated through
 * {@link CommerceCartService}, so calculation hooks run. They deliberately bypass the add-to-cart strategy: that path
 * rejects service products (see {@code ServiceProductAddToCartValidator}).
 */
public class DefaultCartServiceSelectionService implements CartServiceSelectionService
{
	private static final Logger LOG = LoggerFactory.getLogger(DefaultCartServiceSelectionService.class);

	private ProductServiceLookupService productServiceLookupService;
	private ServiceEntryGroupService serviceEntryGroupService;
	private CartService cartService;
	private CommerceCartService commerceCartService;
	private EntryGroupService entryGroupService;
	private ModelService modelService;

	@Override
	public void addService(final CartModel cart, final int productEntryNumber, final String serviceCode)
			throws CartServiceSelectionException
	{
		final AbstractOrderEntryModel productEntry = getProductEntry(cart, productEntryNumber);
		final ProductModel product = productEntry.getProduct();
		final ServiceProductModel service = productServiceLookupService.getAvailableServices(product).stream()
				.filter(s -> Objects.equals(s.getCode(), serviceCode)).findFirst()
				.orElseThrow(() -> new CartServiceSelectionException(
						"Service " + serviceCode + " is not offered for product " + product.getCode()));
		if (findServiceEntry(productEntry, serviceCode).isPresent())
		{
			return;
		}
		if (productServiceLookupService.getServicePrice(service, product).isEmpty())
		{
			throw new CartServiceSelectionException("No price for service " + serviceCode + " on product " + product.getCode());
		}

		final int groupNumber = ensureServiceGroup(cart, productEntry);
		// the service is priced in its own unit, which is also the unit the displayed price uses
		final CartEntryModel serviceEntry = cartService.addNewEntry(cart, service, productEntry.getQuantity().longValue(),
				service.getUnit() != null ? service.getUnit() : productEntry.getUnit(), -1, false);
		serviceEntry.setEntryGroupNumbers(Collections.singleton(Integer.valueOf(groupNumber)));
		try
		{
			modelService.saveAll(serviceEntry, productEntry, cart);
			modelService.refresh(cart);
			recalculate(cart);
		}
		catch (final RuntimeException e)
		{
			// "fails with no change": undo the new entry and group so the cart is not left half-written
			LOG.warn("Adding service {} to cart {} failed, undoing: {}", serviceCode, cart.getCode(), e.getMessage());
			undoAdd(cart, serviceEntry);
			throw new CartServiceSelectionException("Service " + serviceCode + " could not be added: " + e.getMessage());
		}
	}

	@Override
	public void removeService(final CartModel cart, final int productEntryNumber, final String serviceCode)
			throws CartServiceSelectionException
	{
		final AbstractOrderEntryModel productEntry = getProductEntry(cart, productEntryNumber);
		final Optional<AbstractOrderEntryModel> serviceEntry = findServiceEntry(productEntry, serviceCode);
		if (serviceEntry.isEmpty())
		{
			return;
		}
		modelService.remove(serviceEntry.get());
		modelService.refresh(cart);
		removeEmptyServiceGroups(cart);
		normalizeEntryNumbers(cart);
		recalculate(cart);
	}

	@Override
	public boolean syncServiceQuantities(final AbstractOrderEntryModel productEntry)
	{
		final List<AbstractOrderEntryModel> changed = serviceEntryGroupService.getServiceEntries(productEntry).stream()
				.filter(e -> !Objects.equals(e.getQuantity(), productEntry.getQuantity())).collect(Collectors.toList());
		changed.forEach(e -> e.setQuantity(productEntry.getQuantity()));
		modelService.saveAll(changed);
		return !changed.isEmpty();
	}

	@Override
	public List<ServiceProductModel> removeInvalidServices(final CartModel cart)
	{
		return removeInvalid(cart, true);
	}

	@Override
	public List<ServiceProductModel> removeInvalidServicesBeforeCalculation(final CartModel cart)
	{
		return removeInvalid(cart, false);
	}

	@Override
	public boolean hasInvalidServices(final CartModel cart)
	{
		return cart.getEntries() != null && cart.getEntries().stream()
				.anyMatch(e -> serviceEntryGroupService.isServiceEntry(e) && !isValid(e));
	}

	protected void undoAdd(final CartModel cart, final CartEntryModel serviceEntry)
	{
		try
		{
			if (!modelService.isNew(serviceEntry))
			{
				modelService.remove(serviceEntry);
			}
			modelService.refresh(cart);
			removeEmptyServiceGroups(cart);
			normalizeEntryNumbers(cart);
			recalculate(cart);
		}
		catch (final RuntimeException undoFailure)
		{
			LOG.error("Could not undo adding a service to cart {}", cart.getCode(), undoFailure);
		}
	}

	protected List<ServiceProductModel> removeInvalid(final CartModel cart, final boolean recalculate)
	{
		if (cart.getEntries() == null)
		{
			return Collections.emptyList();
		}
		removeEmptyServiceGroups(cart);
		final List<AbstractOrderEntryModel> invalid = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (serviceEntryGroupService.isServiceEntry(entry) && !isValid(entry))
			{
				invalid.add(entry);
			}
		}
		if (invalid.isEmpty())
		{
			return Collections.emptyList();
		}
		final List<ServiceProductModel> removed = invalid.stream().map(e -> (ServiceProductModel) e.getProduct())
				.collect(Collectors.toList());
		LOG.info("Removing {} invalid service entries from cart {}", invalid.size(), cart.getCode());
		modelService.removeAll(invalid);
		modelService.refresh(cart);
		removeEmptyServiceGroups(cart);
		normalizeEntryNumbers(cart);
		if (recalculate)
		{
			recalculate(cart);
		}
		return removed;
	}

	@Override
	public void removeEmptyServiceGroups(final CartModel cart)
	{
		if (cart.getEntryGroups() == null || cart.getEntryGroups().isEmpty())
		{
			return;
		}
		final Set<Integer> emptyServiceGroups = new HashSet<>();
		for (final EntryGroup group : cart.getEntryGroups())
		{
			if (GroupType.SERVICE.equals(group.getGroupType()) && cart.getEntries().stream()
					.noneMatch(e -> serviceEntryGroupService.isServiceEntry(e) && e.getEntryGroupNumbers() != null
							&& e.getEntryGroupNumbers().contains(group.getGroupNumber())))
			{
				emptyServiceGroups.add(group.getGroupNumber());
			}
		}
		if (emptyServiceGroups.isEmpty())
		{
			return;
		}
		final List<AbstractOrderEntryModel> touched = new ArrayList<>();
		for (final AbstractOrderEntryModel entry : cart.getEntries())
		{
			if (entry.getEntryGroupNumbers() != null && entry.getEntryGroupNumbers().stream().anyMatch(emptyServiceGroups::contains))
			{
				final Set<Integer> remaining = new HashSet<>(entry.getEntryGroupNumbers());
				remaining.removeAll(emptyServiceGroups);
				entry.setEntryGroupNumbers(remaining);
				touched.add(entry);
			}
		}
		cart.setEntryGroups(cart.getEntryGroups().stream().filter(g -> !emptyServiceGroups.contains(g.getGroupNumber()))
				.collect(Collectors.toList()));
		modelService.saveAll(touched);
		modelService.save(cart);
	}

	protected boolean isValid(final AbstractOrderEntryModel serviceEntry)
	{
		final Optional<AbstractOrderEntryModel> productEntry = serviceEntryGroupService.getProductEntry(serviceEntry);
		if (productEntry.isEmpty())
		{
			return false;
		}
		final ProductModel product = productEntry.get().getProduct();
		final ServiceProductModel service = (ServiceProductModel) serviceEntry.getProduct();
		return productServiceLookupService.getAvailableServices(product).contains(service)
				&& productServiceLookupService.getServicePrice(service, product).isPresent();
	}

	protected AbstractOrderEntryModel getProductEntry(final CartModel cart, final int entryNumber)
			throws CartServiceSelectionException
	{
		final AbstractOrderEntryModel entry = cart.getEntries() == null ? null
				: cart.getEntries().stream().filter(e -> e.getEntryNumber() != null && e.getEntryNumber().intValue() == entryNumber)
						.findFirst().orElse(null);
		if (entry == null)
		{
			throw new CartServiceSelectionException("Unknown cart entry " + entryNumber);
		}
		if (serviceEntryGroupService.isServiceEntry(entry))
		{
			throw new CartServiceSelectionException("Entry " + entryNumber + " is a service entry");
		}
		return entry;
	}

	protected Optional<AbstractOrderEntryModel> findServiceEntry(final AbstractOrderEntryModel productEntry,
			final String serviceCode)
	{
		return serviceEntryGroupService.getServiceEntries(productEntry).stream()
				.filter(e -> Objects.equals(e.getProduct().getCode(), serviceCode)).findFirst();
	}

	/** Returns the number of the product entry's SERVICE group, creating the group on the cart if needed. */
	protected int ensureServiceGroup(final CartModel cart, final AbstractOrderEntryModel productEntry)
	{
		final Optional<EntryGroup> existing = serviceEntryGroupService.getServiceGroup(productEntry);
		if (existing.isPresent())
		{
			return existing.get().getGroupNumber().intValue();
		}
		final List<EntryGroup> groups = cart.getEntryGroups() == null ? new ArrayList<>() : new ArrayList<>(cart.getEntryGroups());
		final int number = entryGroupService.findMaxGroupNumber(groups) + 1;
		final EntryGroup group = new EntryGroup();
		group.setGroupNumber(Integer.valueOf(number));
		group.setGroupType(GroupType.SERVICE);
		group.setPriority(Integer.valueOf(number));
		group.setErroneous(Boolean.FALSE);
		groups.add(group);
		cart.setEntryGroups(groups);
		final Set<Integer> numbers = productEntry.getEntryGroupNumbers() == null ? new HashSet<>()
				: new HashSet<>(productEntry.getEntryGroupNumbers());
		numbers.add(Integer.valueOf(number));
		productEntry.setEntryGroupNumbers(numbers);
		return number;
	}

	protected void normalizeEntryNumbers(final CartModel cart)
	{
		final List<AbstractOrderEntryModel> sorted = cart.getEntries().stream()
				.sorted(Comparator.comparing(AbstractOrderEntryModel::getEntryNumber)).collect(Collectors.toList());
		final List<AbstractOrderEntryModel> changed = new ArrayList<>();
		for (int i = 0; i < sorted.size(); i++)
		{
			if (sorted.get(i).getEntryNumber().intValue() != i)
			{
				sorted.get(i).setEntryNumber(Integer.valueOf(i));
				changed.add(sorted.get(i));
			}
		}
		modelService.saveAll(changed);
	}

	protected void recalculate(final CartModel cart)
	{
		final CommerceCartParameter parameter = new CommerceCartParameter();
		parameter.setEnableHooks(true);
		parameter.setCart(cart);
		commerceCartService.calculateCart(parameter);
	}

	public void setProductServiceLookupService(final ProductServiceLookupService productServiceLookupService)
	{
		this.productServiceLookupService = productServiceLookupService;
	}

	public void setServiceEntryGroupService(final ServiceEntryGroupService serviceEntryGroupService)
	{
		this.serviceEntryGroupService = serviceEntryGroupService;
	}

	public void setCartService(final CartService cartService)
	{
		this.cartService = cartService;
	}

	public void setCommerceCartService(final CommerceCartService commerceCartService)
	{
		this.commerceCartService = commerceCartService;
	}

	public void setEntryGroupService(final EntryGroupService entryGroupService)
	{
		this.entryGroupService = entryGroupService;
	}

	public void setModelService(final ModelService modelService)
	{
		this.modelService = modelService;
	}
}
