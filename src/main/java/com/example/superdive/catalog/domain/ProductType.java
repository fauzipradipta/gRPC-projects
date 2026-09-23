package com.example.superdive.catalog.domain;

/**
 * Mirrors com.example.superdive.backend.enums.ProductType.
 *
 * DECLARATION ORDER IS LOAD-BEARING. The existing `product` table stores this
 * column as the JPA default ORDINAL, so Retail=0, Course=1, Trip=2, Service=3
 * are the values already on disk. Reordering or inserting a constant silently
 * relabels every existing row.
 */
public enum ProductType {
	Retail,
	Course,
	Trip,
	Service
}
