package moe.shizuku.manager.dhizuku

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import moe.shizuku.manager.R
import moe.shizuku.manager.app.AppBarActivity
import moe.shizuku.manager.databinding.DhizukuAppsActivityBinding
import moe.shizuku.manager.databinding.DhizukuAppItemBinding

/**
 * Dhizuku 本地白名单管理：列出已授权使用 Device Owner 特权的应用，可随时收回。
 */
class DhizukuManageActivity : AppBarActivity() {

    private val adapter = DhizukuAppsAdapter()
    private lateinit var binding: DhizukuAppsActivityBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = DhizukuAppsActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.list.layoutManager = LinearLayoutManager(this)
        binding.list.adapter = adapter
        binding.empty.text = getString(R.string.dhizuku_manage_empty)

        adapter.submit(DhizukuSettings.grantedUids().mapNotNull { uid ->
            val packageName = packageManager.getPackagesForUid(uid)?.firstOrNull()
            if (packageName == null) {
                DhizukuSettings.revoke(uid)
                null
            } else {
                uid to packageName
            }
        })
        updateEmptyState()
    }

    private fun updateEmptyState() {
        binding.empty.visibility = if (adapter.itemCount == 0) View.VISIBLE else View.GONE
    }

    private inner class DhizukuAppsAdapter : RecyclerView.Adapter<DhizukuAppsAdapter.VH>() {

        private var entries: List<Pair<Int, String>> = emptyList()

        fun submit(list: List<Pair<Int, String>>) {
            entries = list
            notifyDataSetChanged()
        }

        fun remove(uid: Int) {
            entries = entries.filter { it.first != uid }
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = DhizukuAppItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return VH(binding)
        }

        override fun getItemCount(): Int = entries.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            holder.bind(entries[position])
        }

        inner class VH(private val binding: DhizukuAppItemBinding) : RecyclerView.ViewHolder(binding.root) {

            fun bind(entry: Pair<Int, String>) {
                val (uid, packageName) = entry
                val ai = runCatching {
                    packageManager.getApplicationInfo(packageName, 0)
                }.getOrNull()

                binding.label.text = runCatching { ai?.loadLabel(packageManager) }.getOrNull()
                    ?: packageName
                binding.packageName.text = packageName
                binding.icon.setImageDrawable(
                    ai?.let { packageManager.getApplicationIcon(it) }
                        ?: packageManager.defaultActivityIcon
                )

                binding.root.setOnClickListener {
                    confirmRevoke(uid, binding.label.text.toString())
                }
            }
        }
    }

    private fun confirmRevoke(uid: Int, label: CharSequence) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.dhizuku_revoke_title)
            .setMessage(getString(R.string.dhizuku_revoke_message, label))
            .setPositiveButton(R.string.dhizuku_revoke_confirm) { _, _ ->
                DhizukuSettings.revoke(uid)
                adapter.remove(uid)
                updateEmptyState()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
