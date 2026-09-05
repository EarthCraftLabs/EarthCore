package de.mecrytv.earthcore.database.internal

import com.google.gson.Gson
import de.mecrytv.earthcore.database.annotations.Table
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.sql.PreparedStatement
import kotlin.reflect.KFunction
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor

internal class ModelSchema<T : Any> private constructor(
    val type: Class<T>,
    val table: String,
    val id: ColumnMeta,
    val columns: List<ColumnMeta>,
    private val create: ((ColumnMeta) -> Any?) -> T,
) {

    private val dataColumns: List<ColumnMeta> = columns.filter { it !== id }

    private val columnList: String = columns.joinToString(", ") { "`${it.name}`" }

    val createTable: String =
        "CREATE TABLE IF NOT EXISTS `$table` (" +
            columns.joinToString(", ") { it.definition } +
            ", PRIMARY KEY (`${id.name}`))"

    val upsert: String = buildString {
        append("INSERT INTO `").append(table).append("` (").append(columnList).append(") VALUES (")
        append(columns.joinToString(", ") { "?" })
        append(") ON DUPLICATE KEY UPDATE ")
        append(
            if (dataColumns.isEmpty()) "`${id.name}` = `${id.name}`"
            else dataColumns.joinToString(", ") { "`${it.name}` = VALUES(`${it.name}`)" },
        )
    }

    val update: String? = dataColumns.takeIf { it.isNotEmpty() }?.let { data ->
        "UPDATE `$table` SET " + data.joinToString(", ") { "`${it.name}` = ?" } +
            " WHERE `${id.name}` = ?"
    }

    val deleteById: String = "DELETE FROM `$table` WHERE `${id.name}` = ?"

    val selectById: String = "SELECT $columnList FROM `$table` WHERE `${id.name}` = ? LIMIT 1"

    val selectAll: String = "SELECT $columnList FROM `$table`"

    fun addColumn(column: ColumnMeta): String = "ALTER TABLE `$table` ADD COLUMN ${column.addition}"

    fun bindAll(statement: PreparedStatement, entity: Any, gson: Gson) =
        columns.forEachIndexed { index, column -> column.bind(statement, index + 1, entity, gson) }

    fun bindForUpdate(statement: PreparedStatement, entity: Any, gson: Gson) {
        dataColumns.forEachIndexed { index, column -> column.bind(statement, index + 1, entity, gson) }
        id.bind(statement, dataColumns.size + 1, entity, gson)
    }

    fun instantiate(read: (ColumnMeta) -> Any?): T = create(read)

    companion object {

        fun <T : Any> of(type: Class<T>): ModelSchema<T> {
            val table = type.getAnnotation(Table::class.java)?.value
                ?: error("${type.simpleName} braucht @Table(name = \"...\")")

            val kotlinClass = if (type.isAnnotationPresent(Metadata::class.java)) type.kotlin else null
            val primary = kotlinClass?.primaryConstructor
            val properties = kotlinClass?.memberProperties?.associateBy { it.name }.orEmpty()

            val fields = persistableFields(type)
            val byName = fields.associateBy { it.name }
            val ordered = primary?.parameters?.map { parameter ->
                byName[parameter.name]
                    ?: error("Konstruktor-Parameter '${parameter.name}' von ${type.simpleName} hat kein Feld")
            } ?: fields

            val columns = ordered.map { ColumnMeta.of(it, properties[it.name]) }
            val keys = columns.filter { it.primaryKey }
            require(keys.size == 1) {
                "${type.simpleName} braucht genau ein @PrimaryKey-Feld, gefunden: ${keys.size}"
            }

            val create =
                if (primary != null) constructorBinding(primary, columns) else fieldBinding(type, columns)

            return ModelSchema(type, Identifiers.check(table), keys.single(), columns, create)
        }

        private fun <T : Any> constructorBinding(
            primary: KFunction<T>,
            columns: List<ColumnMeta>,
        ): ((ColumnMeta) -> Any?) -> T {
            val parameters = primary.parameters.associateBy { it.name }
            return { read -> primary.callBy(columns.associate { parameters.getValue(it.javaField.name) to read(it) }) }
        }

        private fun <T : Any> fieldBinding(
            type: Class<T>,
            columns: List<ColumnMeta>,
        ): ((ColumnMeta) -> Any?) -> T {
            val constructor = runCatching { type.getDeclaredConstructor() }.getOrNull()
                ?: error("${type.simpleName} braucht einen parameterlosen Konstruktor")
            constructor.isAccessible = true
            return { read ->
                constructor.newInstance().also { entity -> columns.forEach { it.set(entity, read(it)) } }
            }
        }

        private fun persistableFields(type: Class<*>): List<Field> {
            val fields = mutableListOf<Field>()
            var current: Class<*>? = type
            while (current != null && current != Any::class.java) {
                fields += current.declaredFields.filter {
                    !it.isSynthetic &&
                        !Modifier.isStatic(it.modifiers) &&
                        !Modifier.isTransient(it.modifiers)
                }
                current = current.superclass
            }
            return fields
        }
    }
}
