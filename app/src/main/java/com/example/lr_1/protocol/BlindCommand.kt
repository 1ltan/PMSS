package com.example.lr_1.protocol

sealed interface BlindCommand {
    fun encode(): String

    data class SetOpening(val percent: Int) : BlindCommand {
        init {
            require(percent in 0..100) { "Opening must be in 0..100" }
        }

        override fun encode() = "SET_OPENING|$percent"
    }

    data object ReadStatus : BlindCommand {
        override fun encode() = "GET_STATUS"
    }
}
