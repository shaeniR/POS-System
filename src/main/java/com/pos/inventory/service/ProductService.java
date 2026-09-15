package com.pos.inventory.service;

import com.pos.inventory.dto.ProductStockResponse;
import com.pos.inventory.model.OrderStatus;
import com.pos.inventory.model.Product;
import com.pos.inventory.repository.OrderItemRepository;
import com.pos.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    @Transactional(readOnly = true)
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    @Transactional
    public Product createProduct(Product product) {
        return productRepository.save(product);
    }

    @Transactional
    public Optional<Product> updateProduct(Long id, Product updatedProduct) {
        return productRepository.findById(id).map(existingProduct -> {
            existingProduct.setName(updatedProduct.getName());
            existingProduct.setPrice(updatedProduct.getPrice());
            existingProduct.setStockCount(updatedProduct.getStockCount());
            return productRepository.save(existingProduct);
        });
    }

    @Transactional
    public boolean deleteProduct(Long id) {
        return productRepository.findById(id).map(product -> {
            productRepository.delete(product);
            return true;
        }).orElse(false);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> getStockLevel(Long id) {
        return productRepository.findById(id).map(Product::getStockCount);
    }

    /**
     * Stock overview for every product: units available for new checkouts and units held by active reservations.
     * Available stock already excludes reserved units, since stock is deducted when a reservation is created.
     */
    @Transactional(readOnly = true)
    public List<ProductStockResponse> getStockOverview() {
        Map<Long, Integer> reservedByProductId = new HashMap<>();
        for (Object[] row : orderItemRepository.sumQuantityByProductForStatus(OrderStatus.RESERVED)) {
            reservedByProductId.put((Long) row[0], ((Number) row[1]).intValue());
        }

        return productRepository.findAll().stream()
                .map(product -> ProductStockResponse.builder()
                        .productId(product.getId())
                        .productName(product.getName())
                        .availableStock(product.getStockCount())
                        .reservedStock(reservedByProductId.getOrDefault(product.getId(), 0))
                        .build())
                .toList();
    }
}
