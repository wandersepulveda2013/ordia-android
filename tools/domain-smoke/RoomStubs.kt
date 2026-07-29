package androidx.room

import kotlin.reflect.KClass

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class ColumnInfo(val defaultValue: String = "")

@Target(AnnotationTarget.CLASS)
annotation class Entity(
    val tableName: String = "",
    val foreignKeys: Array<ForeignKey> = [],
    val indices: Array<Index> = [],
    val primaryKeys: Array<String> = []
)

@Target()
annotation class ForeignKey(
    val entity: KClass<*>,
    val parentColumns: Array<String>,
    val childColumns: Array<String>,
    val onDelete: Int = NO_ACTION,
    val onUpdate: Int = NO_ACTION
) {
    companion object {
        const val NO_ACTION = 1
        const val RESTRICT = 2
        const val SET_NULL = 3
        const val SET_DEFAULT = 4
        const val CASCADE = 5
    }
}

@Target()
annotation class Index(vararg val value: String, val unique: Boolean = false)

@Target(AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
annotation class PrimaryKey(val autoGenerate: Boolean = false)
