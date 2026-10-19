package com.custom.productservices.cart;

import de.hybris.platform.basecommerce.enums.StockLevelStatus;
import de.hybris.platform.commerceservices.stock.impl.DefaultCommerceStockService;
import de.hybris.platform.core.model.product.ProductModel;
import de.hybris.platform.store.BaseStoreModel;
import de.hybris.platform.storelocator.model.PointOfServiceModel;

import com.custom.core.model.ServiceProductModel;


/**
 * NET-8943 &sect;5.5: services are not physical, so they are never blocked by stock. A {@code null} level means "force
 * in stock" to the cart strategies. Everything else delegates to the platform implementation.
 */
public class ServiceAwareCommerceStockService extends DefaultCommerceStockService
{
	@Override
	public Long getStockLevelForProductAndBaseStore(final ProductModel product, final BaseStoreModel baseStore)
	{
		return product instanceof ServiceProductModel ? null : super.getStockLevelForProductAndBaseStore(product, baseStore);
	}

	@Override
	public Long getStockLevelForProductAndPointOfService(final ProductModel product, final PointOfServiceModel pointOfService)
	{
		return product instanceof ServiceProductModel ? null
				: super.getStockLevelForProductAndPointOfService(product, pointOfService);
	}

	@Override
	public StockLevelStatus getStockLevelStatusForProductAndBaseStore(final ProductModel product, final BaseStoreModel baseStore)
	{
		return product instanceof ServiceProductModel ? StockLevelStatus.INSTOCK
				: super.getStockLevelStatusForProductAndBaseStore(product, baseStore);
	}

	@Override
	public StockLevelStatus getStockLevelStatusForProductAndPointOfService(final ProductModel product,
			final PointOfServiceModel pointOfService)
	{
		return product instanceof ServiceProductModel ? StockLevelStatus.INSTOCK
				: super.getStockLevelStatusForProductAndPointOfService(product, pointOfService);
	}
}
