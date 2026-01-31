package com.contest.pocketpharmacist

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.contest.pocketpharmacist.db.AppDb
import com.contest.pocketpharmacist.db.Record
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ReportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report)

        val tvContent = findViewById<TextView>(R.id.tv_report_content)

        lifecycleScope.launch(Dispatchers.IO) {
            val allData = try {
                AppDb.get(this@ReportActivity).dao().getAll()
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList<Record>()
            }

            val aiReport = generateSmartReport(allData)

            withContext(Dispatchers.Main) {
                tvContent.text = aiReport
            }
        }
    }

    private fun generateSmartReport(records: List<Record>): String {
        if (records.isEmpty()) {
            return "【健康档案初始化】\n\n本周暂无用药记录。\n\n💡 建议：多使用拍药功能记录日常用药，AI 助手将为您建立专属健康档案，提供个性化用药提醒与禁忌监测。"
        }

        val totalCount = records.size
        val allMedNames = records.map { it.medName }.distinct().joinToString("、")
        val lastMed = records.last().medName
        val uniqueMeds = records.map { it.medName }.distinct().size

        // 智能建议生成
        val warnings = mutableListOf<String>()
        val tips = mutableListOf<String>()
        var riskLevel = "normal"

        // 抗生素检测（高风险）
        val antibiotics = listOf("头孢", "阿莫西林", "罗红霉素", "阿奇霉素", "左氧氟沙星", "青霉素")
        if (antibiotics.any { allMedNames.contains(it) }) {
            warnings.add("⚠️ 抗生素用药警示\n   检测到抗生素类药物使用。服药期间及停药后7日内严禁饮酒，避免双硫仑样反应（面部潮红、心悸、呼吸困难，严重可致休克）。")
            riskLevel = "high"
        }

        // 感冒/解热镇痛
        val coldMeds = listOf("布洛芬", "对乙酰氨基酚", "感冒", "连花清瘟", "板蓝根", "阿司匹林")
        if (coldMeds.any { allMedNames.contains(it) }) {
            tips.add("🌡️ 感冒护理建议\n   近期有感冒/发热用药记录。建议每日饮水1500-2000ml，保持室内通风，饮食清淡易消化，保证7-8小时睡眠以促进恢复。")
        }

        // 慢性病管理
        val chronicMeds = listOf("降压", "硝苯地平", "氨氯地平", "二甲双胍", "格列美脲", "胰岛素", "阿托伐他汀")
        if (chronicMeds.any { allMedNames.contains(it) }) {
            tips.add("💊 慢病管理提醒\n   慢性病用药需长期坚持，不可擅自停药。建议每日早晚监测血压/血糖并记录，起身时动作放缓，预防体位性低血压导致跌倒。")
        }

        // 消化系统
        val stomachMeds = listOf("奥美拉唑", "雷贝拉唑", "铝碳酸镁", "多潘立酮", "莫沙必利", "胃")
        if (stomachMeds.any { allMedNames.contains(it) }) {
            tips.add("🍵 肠胃养护指南\n   胃部用药期间，忌食辛辣、生冷、油腻食物。建议采用少食多餐（每日5-6餐，每餐七分饱），餐后30分钟内避免平卧。")
        }

        // 默认建议
        if (warnings.isEmpty() && tips.isEmpty()) {
            tips.add("✨ 健康管理建议\n   您的用药记录较为平稳。季节交替之际，注意适时增减衣物，保持适度运动，增强免疫力。")
        }

        // 组装报告
       val reportBuilder = StringBuilder()


        // 统计概览
        reportBuilder.appendLine("【数据概览】")
        reportBuilder.appendLine("📅 分析周期：本周")
        reportBuilder.appendLine("🔢 记录次数：${totalCount} 次")
        reportBuilder.appendLine("💊 用药种类：${uniqueMeds} 种")
        reportBuilder.appendLine("🕐 最近用药：${lastMed}")
        reportBuilder.appendLine()

        // 风险警示（如果有）
        if (warnings.isNotEmpty()) {
            reportBuilder.appendLine("【重要警示】")
            warnings.forEach {
                reportBuilder.appendLine(it)
                reportBuilder.appendLine()
            }
        }

        // 健康建议
        if (tips.isNotEmpty()) {
            reportBuilder.appendLine("【专业建议】")
            tips.forEach {
                reportBuilder.appendLine(it)
                reportBuilder.appendLine()
            }
        }

        // 用药明细
        reportBuilder.appendLine("【本周用药清单】")
        reportBuilder.appendLine("   $allMedNames")
        reportBuilder.appendLine()

        // 用户指定的结尾诗句
        reportBuilder.appendLine("━━━━━━━━━━━━━━")
        reportBuilder.appendLine("🌿 贴心寄语")
        reportBuilder.appendLine("每一粒药都是健康的种子，")
        reportBuilder.appendLine("每一次记录都是关爱的印记。")
        reportBuilder.appendLine("感谢您认真对待自己的健康，")
        reportBuilder.appendLine("愿安康常伴，笑口常开。")
        reportBuilder.appendLine("━━━━━━━━━━━━━━")

        return reportBuilder.toString()
    }
}