package com.payflow.backend.repository;


import com.payflow.backend.domain.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;


import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    List<Product> findByIsActiveTrue();

    List<Product> findByCategoryAndIsActiveTrue(String category);

    List<Product> findByIsActiveTrueAndCategoryOrderByCreatedAtDesc(String category);

    @Query("SELECT p FROM Product p WHERE p.isActive = true " +
            "AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(p.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    List<Product> searchProducts(@Param("searchTerm") String searchTerm);

    @Query("SELECT p FROM Product p WHERE p.isActive = true AND p.isFeatured = true")
    List<Product> findFeaturedProducts();

    @Query("SELECT p FROM Product p WHERE p.quantityInStock - COALESCE(p.reservedQuantity, 0) <= p.lowStockThreshold")
    List<Product> findLowStockProducts();
}