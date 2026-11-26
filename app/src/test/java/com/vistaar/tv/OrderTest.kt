package com.vistaar.tv

import org.junit.Assert.assertEquals
import org.junit.Test

class OrderTest {
    private val id = { s: String -> s }

    @Test fun noSavedOrderKeepsTheDefault() = assertEquals(listOf("a", "b", "c"), Order.arrange(listOf("a", "b", "c"), emptyList(), id))

    @Test fun savedOrderWins() = assertEquals(listOf("c", "a", "b"), Order.arrange(listOf("a", "b", "c"), listOf("c", "a", "b"), id))

    @Test fun unknownKeepDefaultOrder() = assertEquals(listOf("c", "b", "d"), Order.arrange(listOf("b", "d", "c"), listOf("c"), id))

    @Test fun removedItemsInTheSavedOrderAreIgnored() = assertEquals(listOf("b", "a"), Order.arrange(listOf("a", "b"), listOf("x", "b", "a"), id))

    @Test fun moved() = assertEquals(listOf("b", "c", "a"), Order.moved(listOf("a", "b", "c"), 0, 2))
}
