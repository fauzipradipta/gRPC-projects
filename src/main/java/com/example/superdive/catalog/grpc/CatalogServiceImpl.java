package com.example.superdive.catalog.grpc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import com.example.superdive.catalog.domain.ProductRepository;
import com.example.superdive.catalog.domain.ProductType;
import com.example.superdive.catalog.v1.BatchGetProductsRequest;
import com.example.superdive.catalog.v1.BatchGetProductsResponse;
import com.example.superdive.catalog.v1.CatalogServiceGrpc;
import com.example.superdive.catalog.v1.CreateProductRequest;
import com.example.superdive.catalog.v1.GetProductRequest;
import com.example.superdive.catalog.v1.ListProductsRequest;
import com.example.superdive.catalog.v1.Money;
import com.example.superdive.catalog.v1.PricedLine;
import com.example.superdive.catalog.v1.Product;
import com.example.superdive.catalog.v1.RequestedLine;
import com.example.superdive.catalog.v1.ResolvePricesRequest;
import com.example.superdive.catalog.v1.ResolvePricesResponse;

import io.grpc.Status;
import io.grpc.stub.StreamObserver;

@GrpcService
public class CatalogServiceImpl extends CatalogServiceGrpc.CatalogServiceImplBase {

	private final ProductRepository products;

	/*
	 * The `product` table stores a bare DECIMAL with no currency column, so every
	 * row is implicitly in one currency. Naming it here makes that assumption
	 * explicit and gives the wire messages something honest to carry.
	 */
	private final String currencyCode;

	public CatalogServiceImpl(ProductRepository products,
			@Value("${superdive.catalog.currency:IDR}") String currencyCode) {
		this.products = products;
		this.currencyCode = currencyCode;
	}

	@Override
	@Transactional(readOnly = true)
	public void getProduct(GetProductRequest request, StreamObserver<Product> observer) {
		Optional<com.example.superdive.catalog.domain.Product> found = products.findById(request.getId());

		if (found.isEmpty()) {
			// NOT_FOUND rather than an empty Product: a caller must not be able to
			// mistake "no such product" for "a product costing nothing".
			observer.onError(Status.NOT_FOUND
					.withDescription("No product with id " + request.getId())
					.asRuntimeException());
			return;
		}

		observer.onNext(ProductMapper.toProto(found.get(), currencyCode));
		observer.onCompleted();
	}

	@Override
	@Transactional(readOnly = true)
	public void batchGetProducts(BatchGetProductsRequest request,
			StreamObserver<BatchGetProductsResponse> observer) {

		List<com.example.superdive.catalog.domain.Product> found = products.findAllById(request.getIdsList());

		BatchGetProductsResponse.Builder response = BatchGetProductsResponse.newBuilder();
		found.forEach(p -> response.addProducts(ProductMapper.toProto(p, currencyCode)));

		// A partial hit is a normal result, not an error: the caller gets every
		// bad id at once instead of discovering them one failed call at a time.
		java.util.Set<Long> foundIds = found.stream()
				.map(com.example.superdive.catalog.domain.Product::getId)
				.collect(Collectors.toSet());
		request.getIdsList().stream()
				.filter(id -> !foundIds.contains(id))
				.distinct()
				.forEach(response::addMissingIds);

		observer.onNext(response.build());
		observer.onCompleted();
	}

	@Override
	@Transactional(readOnly = true)
	public void listProducts(ListProductsRequest request, StreamObserver<Product> observer) {
		ProductType filter = ProductMapper.toDomain(request.getType());

		List<com.example.superdive.catalog.domain.Product> matches =
				(filter == null) ? products.findAll() : products.findByType(filter);

		// Server-streaming: each product goes out as it is mapped rather than
		// accumulating the whole catalog into one response message.
		matches.forEach(p -> observer.onNext(ProductMapper.toProto(p, currencyCode)));
		observer.onCompleted();
	}

	@Override
	@Transactional(readOnly = true)
	public void resolvePrices(ResolvePricesRequest request, StreamObserver<ResolvePricesResponse> observer) {
		List<Long> ids = request.getLinesList().stream()
				.map(RequestedLine::getProductId)
				.distinct()
				.toList();

		Map<Long, com.example.superdive.catalog.domain.Product> byId = products.findAllById(ids).stream()
				.collect(Collectors.toMap(
						com.example.superdive.catalog.domain.Product::getId,
						Function.identity(),
						(a, b) -> a,
						LinkedHashMap::new));

		ResolvePricesResponse.Builder response = ResolvePricesResponse.newBuilder();
		List<Long> missing = new ArrayList<>();
		BigDecimal runningTotal = BigDecimal.ZERO;

		for (RequestedLine line : request.getLinesList()) {
			com.example.superdive.catalog.domain.Product product = byId.get(line.getProductId());

			if (product == null) {
				if (!missing.contains(line.getProductId())) {
					missing.add(line.getProductId());
				}
				continue;
			}

			if (line.getQty() <= 0) {
				observer.onError(Status.INVALID_ARGUMENT
						.withDescription("Quantity must be positive for product " + line.getProductId())
						.asRuntimeException());
				return;
			}

			// The unit price is read from the catalog, never taken from the
			// request -- the caller cannot price its own basket.
			Money unitPrice = MoneyMapper.toProto(product.getPrice(), currencyCode);
			Money subtotal = MoneyMapper.multiply(unitPrice, line.getQty());
			runningTotal = runningTotal.add(MoneyMapper.toBigDecimal(subtotal));

			response.addLines(PricedLine.newBuilder()
					.setProductId(product.getId())
					.setName(product.getName() == null ? "" : product.getName())
					.setQty(line.getQty())
					.setUnitPrice(unitPrice)
					.setSubtotal(subtotal)
					.build());
		}

		response.setTotal(MoneyMapper.toProto(runningTotal, currencyCode));
		missing.forEach(response::addMissingIds);

		observer.onNext(response.build());
		observer.onCompleted();
	}

	@Override
	@Transactional
	public void createProduct(CreateProductRequest request, StreamObserver<Product> observer) {
		// Mirrors the validation ProductService already performed, but reported as
		// a gRPC status rather than a thrown MessageErrorException.
		if (request.getName().isBlank()) {
			observer.onError(invalidArgument("Product name is required"));
			return;
		}

		ProductType type = ProductMapper.toDomain(request.getType());
		if (type == null) {
			observer.onError(invalidArgument("Product type is required"));
			return;
		}

		if (!request.hasPrice()) {
			observer.onError(invalidArgument("Product price is required"));
			return;
		}

		com.example.superdive.catalog.domain.Product saved = products.save(
				new com.example.superdive.catalog.domain.Product(
						request.getName(),
						type,
						request.getDetails(),
						MoneyMapper.toBigDecimal(request.getPrice())));

		observer.onNext(ProductMapper.toProto(saved, currencyCode));
		observer.onCompleted();
	}

	private static io.grpc.StatusRuntimeException invalidArgument(String message) {
		return Status.INVALID_ARGUMENT.withDescription(message).asRuntimeException();
	}
}
