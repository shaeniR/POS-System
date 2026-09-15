package com.pos.inventory.service;

import com.pos.inventory.dto.AddToCartRequest;
import com.pos.inventory.dto.CartItemResponse;
import com.pos.inventory.dto.CartResponse;
import com.pos.inventory.exception.InsufficientStockException;
import com.pos.inventory.exception.ResourceNotFoundException;
import com.pos.inventory.model.Cart;
import com.pos.inventory.model.CartItem;
import com.pos.inventory.model.Product;
import com.pos.inventory.repository.CartItemRepository;
import com.pos.inventory.repository.CartRepository;
import com.pos.inventory.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;

    @Transactional
    public Cart getOrCreateCartEntity(String cartId) {
        if (cartId == null || cartId.trim().isEmpty()) {
            return createNewCart();
        }
        return cartRepository.findById(cartId).orElseGet(this::createNewCart);
    }

    private Cart createNewCart() {
        Cart cart = Cart.builder()
                .id(UUID.randomUUID().toString())
                .items(new ArrayList<>())
                .build();
        return cartRepository.save(cart);
    }

    @Transactional
    public CartResponse addItemToCart(String cartId, AddToCartRequest request) {
        Cart cart = getOrCreateCartEntity(cartId);

        Product product = productRepository.findById(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with id: " + request.getProductId()));

        if (product.getStockCount() < request.getQuantity()) {
            throw new InsufficientStockException("Insufficient stock for product '" + product.getName() +
                    "'. Requested: " + request.getQuantity() + ", Available: " + product.getStockCount());
        }

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getProduct().getId().equals(product.getId()))
                .findFirst()
                .orElse(null);

        if (existingItem != null) {
            int newQuantity = existingItem.getQuantity() + request.getQuantity();
            if (product.getStockCount() < newQuantity) {
                throw new InsufficientStockException("Insufficient stock for product '" + product.getName() +
                        "'. Total requested in cart: " + newQuantity + ", Available: " + product.getStockCount());
            }
            existingItem.setQuantity(newQuantity);
        } else {
            CartItem newItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(request.getQuantity())
                    .build();
            cart.getItems().add(newItem);
        }

        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart);
    }

    @Transactional(readOnly = true)
    public CartResponse getCart(String cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));
        return mapToCartResponse(cart);
    }

    @Transactional
    public CartResponse removeItemFromCart(String cartId, Long itemId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));

        boolean removed = cart.getItems().removeIf(item -> item.getId().equals(itemId));
        if (!removed) {
            throw new ResourceNotFoundException("Cart item not found with id: " + itemId);
        }

        Cart savedCart = cartRepository.save(cart);
        return mapToCartResponse(savedCart);
    }

    @Transactional
    public void clearCart(String cartId) {
        Cart cart = cartRepository.findById(cartId)
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found with id: " + cartId));
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    public CartResponse mapToCartResponse(Cart cart) {
        List<CartItemResponse> itemResponses = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (CartItem item : cart.getItems()) {
            BigDecimal unitPrice = item.getProduct().getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            total = total.add(subtotal);

            itemResponses.add(CartItemResponse.builder()
                    .id(item.getId())
                    .productId(item.getProduct().getId())
                    .productName(item.getProduct().getName())
                    .unitPrice(unitPrice)
                    .quantity(item.getQuantity())
                    .subtotal(subtotal)
                    .build());
        }

        return CartResponse.builder()
                .cartId(cart.getId())
                .items(itemResponses)
                .totalAmount(total)
                .build();
    }
}
