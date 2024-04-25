package io.emeraldpay.polkaj.ss58

import io.ipfs.multibase.Base58
import org.bouncycastle.jcajce.provider.digest.Blake2b.Blake2b512
import kotlin.experimental.or

object SS58Encoder {

    private val PREFIX = "SS58PRE".toByteArray(Charsets.UTF_8)

    @JvmStatic
    fun encode(ss58Type: SS58Type, value: ByteArray): String {
        require(value.size == 32) {
            //TODO what if some different type, not pubkey?
            "Value length is expected to be 32 bytes, but has: " + value.size
        }
        val normalizedKey = value.publicKeyToSubstrateAccountId()
        val ident = ss58Type.value.toInt() and 0b0011_1111_1111_1111
        val addressTypeByteArray = when (ident) {
            in 0..63 -> byteArrayOf(ident.toByte())
            in 64..16_383 -> {
                val first = (ident and 0b0000_0000_1111_1100) shr 2
                val second = (ident shr 8) or ((ident and 0b0000_0000_0000_0011) shl 6)
                byteArrayOf(first.toByte() or 0b01000000, second.toByte())
            }

            else -> throw IllegalArgumentException("Reserved for future address format extensions")
        }


        //spec says it's 256, but in reality it's 512
        val hash = Blake2b512().digest(PREFIX + addressTypeByteArray + normalizedKey)
        val checksum = hash.copyOfRange(0, PREFIX_SIZE)

        val resultByteArray = addressTypeByteArray + normalizedKey + checksum

        return Base58.encode(resultByteArray)
    }

    @JvmStatic
    @Throws(IllegalArgumentException::class)
    fun decode(ss58String: String): SS58 {
        if (ss58String.isEmpty()) throw IllegalArgumentException("Invalid address")
        val decodedByteArray = Base58.decode(ss58String)
        //should have at least 1 byte of actual data
        require(decodedByteArray.size >= TYPE_LEN + CHECKSUM_LEN + 1) { "Input value is too short" }
        val (prefixLen, ident) = getPrefixLenIdent(decodedByteArray)
        val blake = Blake2b512()
        val hash = blake.digest(PREFIX + decodedByteArray.copyBytes(0, PUBLIC_KEY_SIZE + prefixLen))
        val checkSum = hash.copyBytes(0, PREFIX_SIZE)
        if (!checkSum.contentEquals(decodedByteArray.copyBytes(PUBLIC_KEY_SIZE + prefixLen, PREFIX_SIZE))) {
            throw IllegalArgumentException("Invalid checksum")
        }
        val value = decodedByteArray.copyBytes(prefixLen, PUBLIC_KEY_SIZE)
        return SS58(SS58Type.Network.from(ident.toByte()), value, checkSum)
    }

    fun extractAddressPrefix(address: String): Short {
        val decodedByteArray = Base58.decode(address)
        if (decodedByteArray.size < 2) throw IllegalArgumentException("Invalid address")
        val (_, ident) = getPrefixLenIdent(decodedByteArray)
        return ident
    }

    fun extractAddressByteOrNull(address: String): Short? = try {
        extractAddressPrefix(address)
    } catch (e: Exception) {
        null
    }

    fun ByteArray.publicKeyToSubstrateAccountId() = if (size > 32) {
        Blake2b512().digest(this)
    } else {
        this
    }

    fun String.toAccountId() = decode(this)

    fun String.addressPrefix() = extractAddressPrefix(this)

    fun String.addressByteOrNull() = extractAddressByteOrNull(this)

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun getPrefixLenIdent(decodedByteArray: ByteArray): Pair<Int, Short> {
        return when {
            decodedByteArray[0] in 0..63 -> 1 to decodedByteArray[0].toShort()
            decodedByteArray[0] in 64..127 -> {
                val lower = (decodedByteArray[0].toUByte() shl 2) or (decodedByteArray[1].toUByte() shr 6)
                val upper = (decodedByteArray[1].toUByte() and 0b00111111.toUByte())

                2 to (lower.toInt() or (upper.toInt() shl 8)).toShort()
            }

            else -> throw IllegalArgumentException("Incorrect address byte")
        }
    }

    private const val PREFIX_SIZE = 2
    private const val PUBLIC_KEY_SIZE = 32
    private const val CHECKSUM_LEN = 2
    private const val TYPE_LEN = 1

}

@ExperimentalUnsignedTypes
/**
 * Unsafe to overflow
 */
infix fun UByte.shl(numOfBytes: Int) = (toInt() shl numOfBytes).toUByte()

@ExperimentalUnsignedTypes
/**
 * Unsafe to overflow
 */
infix fun UByte.shr(numOfBytes: Int) = (toInt() shr numOfBytes).toUByte()

fun ByteArray.copyBytes(from: Int, size: Int) = copyOfRange(from, from + size)