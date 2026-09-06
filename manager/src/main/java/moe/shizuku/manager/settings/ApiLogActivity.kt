package moe.shizuku.manager.settings

import android.graphics.Typeface
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import moe.shizuku.manager.R
import moe.shizuku.manager.activation.ActivationRunner
import moe.shizuku.manager.app.AppBarActivity

/**
 * Shizaku API 调用审计日志查看页（RecyclerView 列表，与授权管理页同款结构）。
 *
 * 数据由 server 端 auditCall 写到 /data/local/tmp/shizako-api.log，
 * 本页通过 server 执行 tail 读取（server 未运行时报错提示）。
 * 格式：时间,uid,pid,API,allow/deny
 */
class ApiLogActivity : AppBarActivity() {

    private val adapter = LogAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_log)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val recyclerView = findViewById<RecyclerView>(android.R.id.list)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        reload()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.api_log, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                reload()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun reload() {
        Thread {
            val result = ActivationRunner.run(
                "tail -n 200 /data/local/tmp/shizako-api.log",
                timeoutSeconds = 10
            )
            runOnUiThread {
                val text = when {
                    result.error != null -> getString(R.string.api_log_unavailable)
                    result.output.isBlank() ||
                        result.output.contains("No such file") -> getString(R.string.api_log_empty)
                    else -> result.output
                }
                adapter.submit(text.lineSequence().toList())
            }
        }.start()
    }

    private inner class LogAdapter : RecyclerView.Adapter<LogAdapter.VH>() {

        private var lines: List<String> = emptyList()

        fun submit(list: List<String>) {
            lines = list
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val tv = TextView(parent.context).apply {
                typeface = Typeface.MONOSPACE
                textSize = 12f
                setTextIsSelectable(true)
                val h = resources.getDimensionPixelSize(R.dimen.activity_vertical_margin)
                val v = resources.getDimensionPixelSize(R.dimen.home_margin)
                setPadding(h, v, h, v)
            }
            return VH(tv)
        }

        override fun getItemCount(): Int = lines.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.textView.text = lines[position]
        }

        inner class VH(val textView: TextView) : RecyclerView.ViewHolder(textView)
    }
}
