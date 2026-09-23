package com.example.superdive.catalog.grpc;

import com.example.superdive.catalog.domain.ProductType;
import com.example.superdive.catalog.v1.Product;

/**
 * Entity <-> proto translation.
 */
public final class ProductMapper {

	private static final String PROTO_ENUM_PREFIX = "PRODUCT_TYPE_";

	private ProductMapper() {
	}

	/**
	 * Maps BY NAME, never by number.
	 *
	 * The two enums do not share numbering and never can: proto3 reserves 0 for
	 * UNSPECIFIED, while the database stores the Java ordinal where Retail is 0.
	 * Any numeric mapping shifts every product one type over -- and it would do
	 * so silently, since every value stays in range.
	 */
	public static ProductType toDomain(com.example.superdive.catalog.v1.ProductType proto) {
		if (proto == null
				|| proto == com.example.superdive.catalog.v1.ProductType.PRODUCT_TYPE_UNSPECIFIED
				|| proto == com.example.superdive.catalog.v1.ProductType.UNRECOGNIZED) {
			return null;
		}
		String bare = proto.name().substring(PROTO_ENUM_PREFIX.length());
		for (ProductType candidate : ProductType.values()) {
			if (candidate.name().equalsIgnoreCase(bare)) {
				return candidate;
			}
		}
		throw new IllegalArgumentException("No domain ProductType matching " + proto.name());
	}

	public static com.example.superdive.catalog.v1.ProductType toProto(ProductType domain) {
		if (domain == null) {
			return com.example.superdive.catalog.v1.ProductType.PRODUCT_TYPE_UNSPECIFIED;
		}
		return com.example.superdive.catalog.v1.ProductType.valueOf(
				PROTO_ENUM_PREFIX + domain.name().toUpperCase());
	}

	public static Product toProto(com.example.superdive.catalog.domain.Product entity, String currencyCode) {
		Product.Builder builder = Product.newBuilder()
				.setType(toProto(entity.getType()))
				.setPrice(MoneyMapper.toProto(entity.getPrice(), currencyCode));

		if (entity.getId() != null) {
			builder.setId(entity.getId());
		}
		// Proto3 scalars cannot hold null; a null column becomes the empty string.
		if (entity.getName() != null) {
			builder.setName(entity.getName());
		}
		if (entity.getDetails() != null) {
			builder.setDetails(entity.getDetails());
		}
		return builder.build();
	}
}
