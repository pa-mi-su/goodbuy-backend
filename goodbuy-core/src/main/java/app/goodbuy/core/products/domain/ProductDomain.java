package app.goodbuy.core.products.domain;

/**
 * High-level domains GoodBuy cares about.
 *
 * CLEANING is the only "supported" domain for MVP.
 * Others are here so we can progressively turn them on later.
 */
public enum ProductDomain {
    CLEANING,
    BABY,
    FOOD,
    OTHER,
    UNKNOWN
}
