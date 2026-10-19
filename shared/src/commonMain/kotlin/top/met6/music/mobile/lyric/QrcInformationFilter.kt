package top.met6.music.mobile.lyric

import top.met6.amll.LyricLine
import top.met6.music.mobile.models.Song

/** Shared QRC/YRC metadata rules, kept in sync with iOS QRCInformationFilter. */
internal object QrcInformationFilter {
    fun remove(lines: List<LyricLine>, song: Song?): List<LyricLine> {
        val firstContent = lines.indexOfFirst { it.text.isNotBlank() }
        val artists = song?.artists.orEmpty().map { it.name.trim() }.filter { it.isNotEmpty() }
        val names = (listOf(song?.name.orEmpty().trim()) + artists).filter { it.isNotEmpty() }
        return lines.filterIndexed { index, line ->
            val text = line.text.trim()
            val title = index == firstContent && (
                ('-' in text && names.any { it in text }) ||
                    (line.startTime == 0L && titleSeparator.containsMatchIn(text))
                )
            val artistLabel = (text.endsWith(":") || text.endsWith("：")) && artists.any { it in text }
            val plan = "计划" in text && listOf("酷狗", "音乐人", "QQ音乐").any { it in text }
            val untimed = line.startTime == 0L && line.endTime == 0L &&
                line.words.all { it.startTime == it.endTime }
            !(title || artistLabel || plan || untimed || isCreditHeader(text) ||
                copyright.containsMatchIn(text) || byCredit.containsMatchIn(text))
        }
    }

    private val titleSeparator = Regex("""\s-\s""")
    private val copyright = Regex("""未[经經].*[许許]可.*(?:不得|禁止)""")
    private val byCredit = Regex("""by[:：]""", RegexOption.IGNORE_CASE)

    // Match the complete header with position states, avoiding regex backtracking
    // for repeated roles and retaining ordinary lyric text that contains colons.
    private fun isCreditHeader(text: String): Boolean {
        val colon = text.indexOfFirst { it == ':' || it == '：' }
        if (colon < 0) return false
        val head = text.substring(0, colon).trim().lowercase()
        val count = head.length
        if (count == 0) return false
        val unitStart = BooleanArray(count + 1)
        val afterPrefix = BooleanArray(count + 1)
        val afterRole = BooleanArray(count + 1)
        unitStart[0] = true
        fun advance(index: Int, tokens: List<String>, states: BooleanArray) {
            for (token in tokens) {
                if (head.startsWith(token, index)) states[index + token.length] = true
            }
        }
        for (index in 0..count) {
            if (afterRole[index]) {
                if (index == count) return true
                advance(index, suffixes, afterRole)
                var next = index
                while (next < count && head[next].isWhitespace()) next++
                if (next < count && head[next] in "/／&") {
                    next++
                    while (next < count && head[next].isWhitespace()) next++
                }
                unitStart[index] = true
                unitStart[next] = true
            }
            if (unitStart[index]) advance(index, prefixes, afterPrefix)
            if (unitStart[index] || afterPrefix[index]) advance(index, roles, afterRole)
        }
        return false
    }

    private val roles = listOf(
        "作词", "作曲", "说唱作曲", "原唱", "中文填词", "说唱填词",
        "填词", "改编词", "词曲", "作词作曲", "填詞", "中文填詞",
        "词", "曲", "原", "作詞", "詞", "总制作人",
        "联合制作人", "音乐制作", "监制", "音乐总监", "项目总监", "营销推广",
        "编曲", "編曲", "制作", "總製作人", "聯合製作人", "製作",
        "音樂製作", "監製", "混音", "母带", "和声", "和音",
        "配唱", "人声", "录音勤务", "录音", "声乐指导", "配乐",
        "音频编辑", "剪辑", "音乐编辑", "母帶", "錄音", "剪輯",
        "和聲", "器乐团队", "戏曲", "引用", "乐队", "吉他",
        "贝斯", "鼓", "键盘", "弦乐", "管乐", "打击乐",
        "演唱", "合唱", "貝斯", "鍵盤", "弦樂", "出品",
        "特别鸣谢", "歌曲营销", "策划", "策劃", "运营", "運營",
        "推广", "推廣", "项目企划", "企划", "统筹", "总监",
        "總監", "版权", "版權", "发行", "商务", "营销",
        "宣传", "發行", "企劃", "統籌", "OP", "SP",
        "Lyrics", "Lyricist", "Lyrics by", "Written by", "Words by", "Music",
        "Music by", "Composer", "Composed by", "Arranger", "Arranged by", "Arrangement",
        "Producer", "Produced by", "Executive Producer", "Co-Producer", "Mixing", "Mixed by",
        "Mixing Engineer", "Mastering", "Mastered by", "Mastering Engineer", "Recording", "Recorded by",
        "Recording Engineer", "Vocal Director", "Backing Vocals", "Vocal", "Vocals", "Backing Vocal",
        "Crowd Vocals", "Studio", "Guitar", "Bass", "Drums", "Keyboard",
        "Piano", "Strings", "Programming", "Publisher", "Label", "Coordinator"
    ).map { it.lowercase() }

    private val prefixes = listOf(
        "音频", "音頻", "总", "總", "大", "联合",
        "聯合", "共同", "执行", "執行", "项目", "項目",
        "艺术", "藝術", "音乐", "音樂", "人声", "人聲",
        "弦乐", "弦樂", "乐队", "樂隊", "和声", "和聲",
        "歌曲", "版权", "版權", "宣传", "宣傳"
    ).map { it.lowercase() }

    private val suffixes = listOf(
        "师", "師", "工程师", "工程師", "管理", "后期",
        "後期", "室", "棚", "助理", "统筹", "統籌",
        "处理", "處理", "录音", "錄音", "编写", "編寫",
        "指导", "指導", "协力", "協力", "总监", "總監",
        "监制", "監製", "编辑", "編輯", "策划", "策劃",
        "企划", "企劃", "人", "方"
    ).map { it.lowercase() }
}
