package de.mecrytv.earthcore.database.internal

import com.google.gson.Gson
import de.mecrytv.earthcore.database.annotations.Column
import de.mecrytv.earthcore.database.annotations.JsonColumn
import de.mecrytv.earthcore.database.annotations.PrimaryKey
import java.lang.reflect.Field
import java.lang.reflect.Type
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Types
import java.util.UUID
import kotlin.reflect.KProperty1
import kotlin.reflect.full.findAnnotation

internal enum class ColumnKind { STRING, UUID, ENUM, INT, LONG, SHORT, BOOLEAN, DOUBLE, FLOAT, JSON }

internal class ColumnMeta private constructor(
    val name: String,
    val field: Field,
    val primaryKey: Boolean,
    private val kind: ColumnKind,
    private val javaType: Type,
    private val nullable: Boolean,
    private val length: Int,
) {

    val definition: String get() = "`$name` $sqlType" + if (nullable) "" else " NOT NULL"

    private val sqlType: String
        get() = when (kind) {
            ColumnKind.STRING, ColumnKind.ENUM -> "VARCHAR($length)"
            ColumnKind.UUID -> "CHAR(36)"
            ColumnKind.INT -> "INT"
            ColumnKind.LONG -> "BIGINT"
            ColumnKind.SHORT -> "SMALLINT"
            ColumnKind.BOOLEAN -> "BOOLEAN"
            ColumnKind.DOUBLE -> "DOUBLE"
            ColumnKind.FLOAT -> "FLOAT"
            ColumnKind.JSON -> "LONGTEXT"
        }

    fun bind(statement: PreparedStatement, index: Int, entity: Any, gson: Gson) =
        bindValue(statement, index, field.get(entity), gson)

    fun bindValue(statement: PreparedStatement, index: Int, value: Any?, gson: Gson) {
        if (value == null) {
            statement.setNull(index, Types.NULL)
            return
        }
        when (kind) {
            ColumnKind.JSON -> statement.setString(index, gson.toJson(value, javaType))
            ColumnKind.UUID -> statement.setString(index, value.toString())
            ColumnKind.ENUM -> statement.setString(index, (value as Enum<*>).name)
            else -> statement.setObject(index, value)
        }
    }

    fun set(entity: Any, value: Any?) = field.set(entity, value)

    fun read(row: ResultSet, gson: Gson): Any? = when (kind) {
        ColumnKind.JSON -> row.getString(name)?.let { gson.fromJson(it, javaType) }
        ColumnKind.STRING -> row.getString(name)
        ColumnKind.UUID -> row.getString(name)?.let(UUID::fromString)
        ColumnKind.ENUM -> row.getString(name)?.let(::toEnum)
        ColumnKind.INT -> row.orNull(row.getInt(name))
        ColumnKind.LONG -> row.orNull(row.getLong(name))
        ColumnKind.SHORT -> row.orNull(row.getShort(name))
        ColumnKind.BOOLEAN -> row.orNull(row.getBoolean(name))
        ColumnKind.DOUBLE -> row.orNull(row.getDouble(name))
        ColumnKind.FLOAT -> row.orNull(row.getFloat(name))
    }

    private fun <V> ResultSet.orNull(value: V): V? = if (wasNull()) null else value

    private fun toEnum(raw: String): Any = field.type.enumConstants
        ?.firstOrNull { (it as Enum<*>).name == raw }
        ?: error("'$raw' ist keine gueltige Konstante von ${field.type.simpleName} (Spalte '$name')")

    override fun toString(): String = "$name $sqlType"

    companion object {

        fun of(field: Field, property: KProperty1<*, *>?): ColumnMeta {
            field.isAccessible = true
            val json = field.getAnnotation(JsonColumn::class.java) ?: property?.findAnnotation<JsonColumn>()
            val column = field.getAnnotation(Column::class.java) ?: property?.findAnnotation<Column>()
            val key = field.getAnnotation(PrimaryKey::class.java) ?: property?.findAnnotation<PrimaryKey>()

            return ColumnMeta(
                name = Identifiers.check(
                    json?.name?.takeIf { it.isNotEmpty() }
                        ?: column?.name?.takeIf { it.isNotEmpty() }
                        ?: field.name,
                ),
                field = field,
                primaryKey = key != null,
                kind = if (json != null) ColumnKind.JSON else kindOf(field.type),
                javaType = field.genericType,
                nullable = property?.returnType?.isMarkedNullable ?: !field.type.isPrimitive,
                length = column?.length ?: 255,
            )
        }

        private fun kindOf(raw: Class<*>): ColumnKind = when (raw) {
            String::class.java -> ColumnKind.STRING
            UUID::class.java -> ColumnKind.UUID
            Integer::class.java, Integer.TYPE -> ColumnKind.INT
            java.lang.Long::class.java, java.lang.Long.TYPE -> ColumnKind.LONG
            java.lang.Short::class.java, java.lang.Short.TYPE -> ColumnKind.SHORT
            java.lang.Boolean::class.java, java.lang.Boolean.TYPE -> ColumnKind.BOOLEAN
            java.lang.Double::class.java, java.lang.Double.TYPE -> ColumnKind.DOUBLE
            java.lang.Float::class.java, java.lang.Float.TYPE -> ColumnKind.FLOAT
            else -> if (raw.isEnum) ColumnKind.ENUM else ColumnKind.JSON
        }
    }
}
