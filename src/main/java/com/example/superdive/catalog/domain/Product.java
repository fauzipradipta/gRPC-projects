package com.example.superdive.catalog.domain;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The catalog service owns this table. The `customer` association that used to
 * sit on the backend's copy of this entity is deliberately absent: it was never
 * populated by any code path, and a catalog product is something you sell
 * rather than something a customer owns. Keeping it would make this service
 * depend on Customer, which is the coupling the split exists to remove.
 */
@Entity
@Table(name = "product")
public class Product {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "product_id")
	private Long id;

	private String name;

	/*
	 * ORDINAL is stated explicitly rather than left to the JPA default. The
	 * default happens to be ORDINAL, which is what the existing rows use, but
	 * relying on that silently is how this column gets "tidied up" to STRING one
	 * day and takes every existing row with it.
	 */
	@Enumerated(EnumType.ORDINAL)
	private ProductType type;

	private String details;

	private BigDecimal price;

	protected Product() {
		// for JPA
	}

	public Product(String name, ProductType type, String details, BigDecimal price) {
		this.name = name;
		this.type = type;
		this.details = details;
		this.price = price;
	}

	public Long getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public ProductType getType() {
		return type;
	}

	public void setType(ProductType type) {
		this.type = type;
	}

	public String getDetails() {
		return details;
	}

	public void setDetails(String details) {
		this.details = details;
	}

	public BigDecimal getPrice() {
		return price;
	}

	public void setPrice(BigDecimal price) {
		this.price = price;
	}
}
