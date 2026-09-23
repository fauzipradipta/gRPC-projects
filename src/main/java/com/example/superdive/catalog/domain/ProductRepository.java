package com.example.superdive.catalog.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

	/*
	 * Product.type is mapped @Enumerated(ORDINAL), so Spring Data binds the
	 * ordinal automatically and a derived query works directly. The backend
	 * needed a native query with #type.ordinal() only because its entity left
	 * the mapping implicit.
	 */
	List<Product> findByType(ProductType type);
}
