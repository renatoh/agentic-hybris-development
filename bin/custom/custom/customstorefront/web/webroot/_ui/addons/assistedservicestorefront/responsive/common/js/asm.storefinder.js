/*
 * Copyright (c) 2024 SAP SE or an SAP affiliate company. All rights reserved.
 */

ASM.storefinder = {
	storeData: "",
	storeId: "",
	originalStore: "",
	coords: {},
	storeSearchData: {},
	originAddress: "",

	autoLoad: function (data) {
		originAddress = data;
		ASM.storefinder.init();
	},

	createListItemHtml: function (data, id) {

		let $rdioEl = $("<input>")
			.addClass("js-asm-store-finder-input")
			.attr("type", "radio")
			.attr("name", "storeNamePost")
			.attr("id", "asm-store-filder-entry-" + id)
			.attr("data-id", id)
			.val(data.displayName)
		let $spanInfo = $("<span>")
			.addClass("entry__info")
			.append($("<span>").addClass("entry__name").text(data.displayName))
			.append(
			$("<span>")
			.addClass("entry__address")
			.text(data.line1 + " " + data.line2)
			)
			.append($("<span>").addClass("entry__city").text(data.town));
		let $spanDistance = $("<span>")
			.addClass("entry__distance")
			.append($("<span>").text(data.formattedDistance));
		let $label = $("<label>")
			.addClass("js-select-store-label")
			.attr("for", "asm-store-filder-entry-" + id)
			.append($spanInfo)
			.append($spanDistance);

		return $("<li>").addClass("asm__list__entry").append($rdioEl).append($label);
	},

	refreshNavigation: function () {
		var storeData = ASM.storefinder.storeData;
		let $storeList = $(".js-asm-store-finder-navigation-list");
		$storeList.empty();

		if (storeData) {
			for (i = 0; i < storeData["data"].length; i++) {
				$storeList.append(ASM.storefinder.createListItemHtml(storeData["data"][i], i));
			}


			// select the first store
			var firstInput = $(".js-asm-store-finder-input")[0];
			$(firstInput).click();
		}

		var page = ASM.storefinder.storeSearchData.page;
		var to = ((page * 10 + 10) > storeData.total) ? storeData.total : page * 10 + 10;

		$(".js-asm-store-finder-pager-item-from").text(page * 10 + 1);
		$(".js-asm-store-finder-pager-item-to").text(to);
		$(".js-asm-store-finder-pager-item-all").text(storeData.total);
		$(".js-asm-store-finder").removeClass("show-store");

		if (!storeData || !storeData.total || storeData.total === 0) {
			$(".store__finder--panel").hide();
			$(".js-asm-store-finder-pager-page-info").hide();
			$(".asm_store__finder--pagination-footer").hide();
		}
	},


	bindPagination: function () {
		$(document).on("click", ".js-asm-store-finder-pager-prev", function (e) {
			e.preventDefault();
			var page = ASM.storefinder.storeSearchData.page;
			ASM.storefinder.getStoreData(page - 1);
			checkStatus(page - 1);
		});

		$(document).on("click", ".js-asm-store-finder-pager-next", function (e) {
			e.preventDefault();
			var page = ASM.storefinder.storeSearchData.page;
			ASM.storefinder.getStoreData(page + 1);
			checkStatus(page + 1);
		});

		function checkStatus(page) {
			if (page === 0) {
				$(".js-asm-store-finder-pager-prev").attr("disabled", "disabled");
			} else {
				$(".js-asm-store-finder-pager-prev").removeAttr("disabled");
			}

			if (page === Math.floor(ASM.storefinder.storeData.total / 10)) {
				$(".js-asm-store-finder-pager-next").attr("disabled", "disabled");
			} else {
				$(".js-asm-store-finder-pager-next").removeAttr("disabled");
			}
		}

	},

	bindStoreChange: function () {
		$(document).on("change", ".js-asm-store-finder-input", function (e) {
			e.preventDefault();



			storeData = ASM.storefinder.storeData["data"];

			var storeId = $(this).data("id");

			var $ele = $(".js-asm-store-finder-details");



			$.each(storeData[storeId], function (key, value) {
				if (key === "image") {
					$ele.find(".js-asm-store-image").empty();
					if (value !== "") {
						$ele.find(".js-asm-store-image").append($("<img>").attr("src", value).attr("alt", ""));
					}
				} else if (key === "productcode") {
					$ele.find(".js-asm-store-productcode").val(value);
				}
				else if (key === "openings") {
					var $oele = $ele.find(".js-asm-store-" + key);
					$oele.empty();
					if (value !== "") {
						$.each(value, function (key2, value2) {
							$oele.append($("<dt>").text(key2));
							$oele.append($("<dd>").text(value2));
						});
					}
				}
				else if (key === "features") {
					let $features = $ele.find(".js-asm-store-" + key);
					$features.empty();
					$.each(value,function(key2,value2){
						$features.append($("<li>").text(value2));
					});
				}
				else {
					if (value !== "") {
						$ele.find(".js-asm-store-" + key).text(value);
					} else {
						$ele.find(".js-asm-store-" + key).text('');
					}
				}

			});

			ASM.storefinder.storeId = storeData[storeId];
			ASM.storefinder.initGoogleMap();

		});
	},



	initGoogleMap: function () {

		if ($(".js-asm-store-finder-map").length > 0) {
			ACC.global.addGoogleMapsApi("ASM.storefinder.loadGoogleMap");
		}
	},

	loadGoogleMap: function () {

		storeInformation = ASM.storefinder.storeId;

		if ($(".js-asm-store-finder-map").length > 0) {
			$(".js-asm-store-finder-map").attr("id", "asm-store-finder-map");
			var centerPoint = new google.maps.LatLng(storeInformation["latitude"], storeInformation["longitude"]);

			var mapOptions = {
				zoom: 16,
				zoomControl: true,
				panControl: true,
				streetViewControl: false,
				mapTypeId: google.maps.MapTypeId.ROADMAP,
				center: centerPoint
			};

			var map = new google.maps.Map(document.getElementById("asm-store-finder-map"), mapOptions);

			// Prevent the store info has been stored, when the first page is loaded, which is needed only for the driving instructions.
			if (ASM.storefinder.originalStore === "") {
				ASM.storefinder.originalStore = data["data"][0];
			}

			//Driving Options
			if (ACC.config.googleApiKey !== "" && ASM.storefinder.originalStore["latitude"] !== storeInformation["latitude"]) {
				var directionsDisplay = new google.maps.DirectionsRenderer();
				var directionsService = new google.maps.DirectionsService();
				var originPoint = new google.maps.LatLng(ASM.storefinder.originalStore["latitude"], ASM.storefinder.originalStore["longitude"]);
				directionsDisplay.setMap(map);
				var request = {
					origin: originPoint,
					destination: centerPoint,
					travelMode: 'DRIVING'
				};

				directionsService.route(request, function (response, status) {
					if (status === 'OK') {
						directionsDisplay.setDirections(response);
					}
				});
			}
			// Driving Options

			var marker = new google.maps.Marker({
				position: new google.maps.LatLng(storeInformation["latitude"], storeInformation["longitude"]),
				map: map,
				title: storeInformation["name"],
				icon: "https://maps.google.com/mapfiles/marker" + 'A' + ".png"
			});

			var infowindow = new google.maps.InfoWindow({
				content: ACC.common.encodeHtml(storeInformation["name"]),
				disableAutoPan: true
			});

			google.maps.event.addListener(marker, 'click', function () {
				var mapWindow = infowindow.open(map, marker, 'noopener,noreferrer');
				mapWindow.opener = null;
			});

			var markerPosition = storeInformation["latitude"] + "," + storeInformation["longitude"];
			map.addListener('click', function (e) {
				if (ACC.config.googleApiKey !== "") {
					window.open("https://www.google.de/maps/dir/" + encodeURIComponent(originAddress) + "/" + encodeURIComponent(markerPosition), '_blank', 'noopener,noreferrer');
				}

				else {
					window.open("https://www.google.de/maps/dir/" + encodeURIComponent(markerPosition), '_blank', 'noopener,noreferrer');
				}
			});
		}

	},

	getStoreData: function (page) {
		ASM.storefinder.storeSearchData.page = page;
		url = $(".js-asm-store-finder").data("url");
		$.ajax({
			url: url,
			data: ASM.storefinder.storeSearchData,
			type: "get",
			success: function (response) {
				var storeData;
				try {
					storeData = $.parseJSON(response);
				} catch (e) {
					storeData = { total: 0, data: [] };
				}
				ASM.storefinder.storeData = storeData;
				ASM.storefinder.refreshNavigation();
				if (ASM.storefinder.storeData.total < 10) {
					$(".js-asm-store-finder-pager-next").attr("disabled", "disabled");
				}
			}
		});
	},

	getInitStoreData: function (q, latitude, longitude) {
		$(".alert").remove();
		data = {
			"q": "",
			"page": 0
		};
		if (q != null) {
			data.q = q;
		}

		if (latitude != null) {
			data.latitude = latitude;
		}

		if (longitude != null) {
			data.longitude = longitude;
		}

		ASM.storefinder.storeSearchData = data;
		ASM.storefinder.getStoreData(data.page);
		$(".js-asm-store-finder").show();
		$(".js-asm-store-finder-pager-prev").attr("disabled", "disabled");
		$(".js-asm-store-finder-pager-next").removeAttr("disabled");
	},

	init: function () {
		$("#findStoresNearMe").attr("disabled", "disabled");
		if (navigator.geolocation) {
			navigator.geolocation.getCurrentPosition(
				function (position) {
					ASM.storefinder.coords = position.coords;
					$('#findStoresNearMe').removeAttr("disabled");
				},
				function (error) {
					console.debug("An error occurred... The error code and message are: " + error.code + "/" + error.message);
				}
			);
		}
	}
};

$(document).ready(
	function () {
		ASM.storefinder.bindStoreChange();
		ASM.storefinder.bindPagination();
	}
)
