package com.dialaboral.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("dia_laboral", MODE_PRIVATE) }
    private var spanish = false
    private val boxNames = mutableListOf("A", "B", "C", "D")
    private val boxInputs = linkedMapOf<String, EditText>()
    private lateinit var root: LinearLayout
    private lateinit var boxContainer: LinearLayout
    private lateinit var hoursInput: EditText
    private lateinit var rateInput: EditText
    private lateinit var totalLabel: TextView
    private lateinit var historyLabel: TextView
    private val records = mutableListOf<JSONObject>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        spanish = prefs.getBoolean("spanish", false)
        val savedBoxes = prefs.getString("boxes", "A,B,C,D") ?: "A,B,C,D"
        boxNames.clear()
        boxNames.addAll(savedBoxes.split(",").filter { it.isNotBlank() })
        if (boxNames.isEmpty()) boxNames.addAll(listOf("A", "B", "C", "D"))
        val arr = JSONArray(prefs.getString("records", "[]") ?: "[]")
        for (i in 0 until arr.length()) records.add(arr.getJSONObject(i))
        buildUi()
    }

    private fun t(ar: String, es: String) = if (spanish) es else ar
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()

    private fun buildUi() {
        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(24))
            layoutDirection = if (spanish) View.LAYOUT_DIRECTION_LTR else View.LAYOUT_DIRECTION_RTL
        }
        scroll.addView(root)
        setContentView(scroll)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "Día Laboral"; textSize = 26f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        }
        val lang = Button(this).apply {
            text = if (spanish) "العربية" else "Español"
            setOnClickListener { spanish = !spanish; prefs.edit().putBoolean("spanish", spanish).apply(); buildUi() }
        }
        top.addView(title); top.addView(lang); root.addView(top)
        addText(t("حساب يوم العمل", "Cálculo de jornada"), 20f, true)
        addText(t("عدد الصناديق حسب النوع", "Cantidad de cajas por tipo"), 16f, true)
        boxContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(boxContainer)
        boxInputs.clear()
        boxNames.forEach { addBoxRow(it, "0") }
        addButton(t("+ إضافة صندوق جديد", "+ Añadir otra caja")) { addNewBox() }
        addText(t("ساعات العمل", "Horas trabajadas"), 16f, true)
        hoursInput = edit("0", t("عدد الساعات", "Número de horas"), true)
        root.addView(hoursInput)
        addText(t("ثمن الساعة (€)", "Precio por hora (€)"), 16f, true)
        rateInput = edit(prefs.getString("rate", "9.30") ?: "9.30", t("ثمن الساعة", "Precio por hora"), true)
        root.addView(rateInput)
        addButton(t("احسب المجموع", "Calcular total")) { calculate() }
        totalLabel = TextView(this).apply { textSize = 22f; setTypeface(null, Typeface.BOLD); setPadding(0, dp(10), 0, dp(10)) }
        root.addView(totalLabel)
        addButton(t("حفظ يوم العمل", "Guardar jornada")) { saveDay() }
        addButton(t("تصدير البيانات إلى Excel", "Exportar datos a Excel")) { exportExcel() }
        addText(t("السجل اليومي", "Historial de jornadas"), 20f, true)
        historyLabel = TextView(this).apply { textSize = 14f; setPadding(0, dp(8), 0, dp(8)) }
        root.addView(historyLabel)
        updateHistory()
        calculate()
    }

    private fun addText(value: String, size: Float, bold: Boolean) {
        root.addView(TextView(this).apply {
            text = value; textSize = size
            if (bold) setTypeface(null, Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(5))
        })
    }

    private fun edit(value: String, hintValue: String, numeric: Boolean): EditText =
        EditText(this).apply {
            setText(value); hint = hintValue
            if (numeric) inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            textSize = 16f
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }

    private fun addBoxRow(name: String, value: String) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply {
            text = t("الصندوق ", "Caja ") + name
            textSize = 16f; setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(dp(100), -2)
        })
        val input = edit(value, "0", true)
        input.layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
        boxInputs[name] = input
        row.addView(input)
        boxContainer.addView(row)
    }

    private fun addButton(label: String, action: () -> Unit) {
        root.addView(Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } })
    }

    private fun addNewBox() {
        val name = nextBoxName()
        boxNames.add(name)
        prefs.edit().putString("boxes", boxNames.joinToString(",")).apply()
        addBoxRow(name, "0")
    }

    private fun nextBoxName(): String {
        var n = boxNames.size
        var result = ""
        while (n >= 0) {
            result = ('A'.code + (n % 26)).toChar() + result
            n = n / 26 - 1
            if (n < 0) break
        }
        var candidate = result
        while (boxNames.contains(candidate)) candidate += "A"
        return candidate
    }

    private fun number(input: EditText): Double = input.text.toString().replace(',', '.').toDoubleOrNull() ?: 0.0
    private fun money(n: Double) = String.format(Locale.getDefault(), "%.2f €", n)

    private fun calculate(): Double {
        val total = number(hoursInput) * number(rateInput)
        totalLabel.text = t("المجموع: ", "Total: ") + money(total)
        prefs.edit().putString("rate", rateInput.text.toString()).apply()
        return total
    }

    private fun saveDay() {
        val boxes = JSONObject()
        var count = 0
        boxInputs.forEach { (name, input) ->
            val v = number(input).toInt()
            boxes.put(name, v); count += v
        }
        val total = calculate()
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val record = JSONObject().put("date", date).put("hours", number(hoursInput)).put("rate", number(rateInput))
            .put("boxes", boxes).put("boxCount", count).put("total", total)
        records.add(0, record)
        prefs.edit().putString("records", JSONArray(records).toString()).putString("boxes", boxNames.joinToString(",")).apply()
        updateHistory()
        Toast.makeText(this, t("تم حفظ يوم العمل", "Jornada guardada"), Toast.LENGTH_SHORT).show()
    }

    private fun updateHistory() {
        if (!::historyLabel.isInitialized) return
        val sum = records.sumOf { it.optDouble("total") }
        val lines = records.take(30).map { r ->
            val boxes = r.optJSONObject("boxes") ?: JSONObject()
            val details = boxes.keys().asSequence().map { "$it:" + boxes.optInt(it) }.joinToString("  ")
            r.optString("date") + " | " + money(r.optDouble("total")) + " | " + r.optDouble("hours") + " h | " + details
        }
        historyLabel.text = (lines.ifEmpty { listOf(t("لا توجد بيانات محفوظة بعد", "Todavía no hay jornadas guardadas")) }).joinToString("\n\n") +
            "\n\n" + t("المجموع التراكمي: ", "Total acumulado: ") + money(sum)
    }

    private fun exportExcel() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/vnd.ms-excel"
            putExtra(Intent.EXTRA_TITLE, "DiaLaboral.xls")
        }
        startActivityForResult(intent, 42)
    }

    @Deprecated("Deprecated in Android but compatible with older devices")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK && data?.data != null) {
            try {
                val html = buildString {
                    append("<html><head><meta charset='UTF-8'></head><body><table border='1'><tr><th>Date</th><th>Hours</th><th>Hourly rate</th>")
                    boxNames.forEach { append("<th>Box " + it + "</th>") }
                    append("<th>Box count</th><th>Total EUR</th></tr>")
                    records.forEach { r ->
                        val boxes = r.optJSONObject("boxes") ?: JSONObject()
                        append("<tr><td>" + r.optString("date") + "</td><td>" + r.optDouble("hours") + "</td><td>" + r.optDouble("rate") + "</td>")
                        boxNames.forEach { append("<td>" + boxes.optInt(it) + "</td>") }
                        append("<td>" + r.optInt("boxCount") + "</td><td>" + r.optDouble("total") + "</td></tr>")
                    }
                    append("</table></body></html>")
                }
                contentResolver.openOutputStream(data.data!!)?.bufferedWriter(Charsets.UTF_8)?.use { it.write(html) }
                Toast.makeText(this, t("تم تصدير ملف Excel", "Archivo Excel exportado"), Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Export error: " + e.message, Toast.LENGTH_LONG).show()
            }
        }
    }
}
