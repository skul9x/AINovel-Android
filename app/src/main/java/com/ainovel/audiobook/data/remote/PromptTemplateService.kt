package com.ainovel.audiobook.data.remote

import com.ainovel.audiobook.domain.model.NovelTemplate

class PromptTemplateService {

    /**
     * Sanitizes chapter titles by removing decorative brackets, markdown asterisks, and numbering noise.
     */
    fun sanitizeChapterTitle(rawTitle: String): String {
        var title = rawTitle.trim()
        // Strip markdown bold / italic
        title = title.replace(Regex("""^\*+|\*+$"""), "").trim()
        // Strip surrounding brackets: [ ... ], 《 ... 》, ( ... ), { ... }, 【 ... 】
        title = title.replace(Regex("""^(\[|《|\(|【|\{)\s*"""), "")
            .replace(Regex("""\s*(\]|》|\)|】|\})$"""), "")
            .trim()
        // Strip markdown again in case brackets were inside
        title = title.replace(Regex("""^\*+|\*+$"""), "").trim()
        return title
    }

    /**
     * Builds prompt for generating an outline with chapter titles from a topic/synopsis.
     */
    fun buildOutlinePrompt(
        topic: String,
        genre: String,
        totalChapters: Int,
        language: String = "vi"
    ): Pair<String, String> {
        val template = NovelTemplate.findById(genre)
        val systemPrompt = if (language == "vi") template.systemPromptVi else template.systemPromptEn

        val userPrompt = if (language == "vi") {
            """
            Hãy xây dựng đề cương chi tiết cho tác phẩm có đề tài sau:
            ĐỀ TÀI / SYNOPSIS:
            $topic

            THỂ LOẠI: ${template.nameVi}
            SỐ LƯỢNG CHƯƠNG DỰ KIẾN: $totalChapters chương.

            YÊU CẦU:
            1. Tóm tắt thế giới quan (Worldview) và hồ sơ các nhân vật chính.
            2. Liệt kê danh sách đúng $totalChapters chương theo định dạng chuẩn xác từng dòng:
               Chương 1: [Tiêu đề chương 1]
               Chương 2: [Tiêu đề chương 2]
               ...
            3. Dưới mỗi tiêu đề chương, ghi 2-3 câu tóm tắt cốt lõi sự kiện xảy ra trong chương đó.
            """.trimIndent()
        } else {
            """
            Create a comprehensive novel outline for the following topic:
            TOPIC / SYNOPSIS:
            $topic

            GENRE: ${template.nameEn}
            PLANNED CHAPTERS: $totalChapters chapters.

            REQUIREMENTS:
            1. Summarize world lore and major character profiles.
            2. List exactly $totalChapters chapters using this strict format line-by-line:
               Chapter 1: [Chapter 1 Title]
               Chapter 2: [Chapter 2 Title]
               ...
            3. Below each chapter title, provide a 2-3 sentence synopsis of the core events.
            """.trimIndent()
        }

        return Pair(systemPrompt, userPrompt)
    }

    /**
     * Builds prompt for writing a specific chapter prose using sliding-window context.
     */
    fun buildChapterPrompt(
        novelTitle: String,
        genre: String,
        chapterIndex: Int,
        chapterTitle: String,
        chapterObjective: String,
        recentChaptersText: String, // Up to 3 recent chapters
        grandSummary: String,       // Summary of distant arc
        worldLore: String,          // Active characters and worldbuilding
        language: String = "vi"
    ): Pair<String, String> {
        val template = NovelTemplate.findById(genre)
        val systemPrompt = if (language == "vi") template.systemPromptVi else template.systemPromptEn

        val cleanTitle = sanitizeChapterTitle(chapterTitle)

        val userPrompt = if (language == "vi") {
            """
            Bạn đang viết tiếp tác phẩm: 《$novelTitle》.
            Thể loại: ${template.nameVi}

            === THẾ GIỚI QUAN & NHÂN VẬT (WORLD LORE) ===
            ${worldLore.ifBlank { "Chưa có ghi chú đặc biệt." }}

            === TÓM TẮT TIẾN TRÌNH CỐT TRUYỆN TRƯỚC ĐÓ ===
            ${grandSummary.ifBlank { "Đây là những chương đầu tiên." }}

            === NGỮ CẢNH CÁC CHƯƠNG VỪA XẢY RA (3 CHƯƠNG GẦN NHẤT) ===
            ${recentChaptersText.ifBlank { "Bắt đầu câu chuyện." }}

            === NHIỆM VỤ HIỆN TẠI ===
            Hãy viết toàn văn cho:
            CHƯƠNG $chapterIndex: $cleanTitle
            Mục tiêu sự kiện cần diễn ra trong chương:
            $chapterObjective

            QUY TẮC BẮT BUỘC:
            1. Đi thẳng vào nội dung văn xuôi, KHÔNG mở đầu bằng lời chào hay nhận xét.
            2. Giữ vững tính cách nhân vật và không mâu thuẫn với sự kiện ở các chương trước.
            3. Dung lượng chi tiết, văn phong mượt mà, gợi hình, giàu cảm xúc.
            """.trimIndent()
        } else {
            """
            You are writing the ongoing novel: "$novelTitle".
            Genre: ${template.nameEn}

            === WORLD LORE & CHARACTERS ===
            ${worldLore.ifBlank { "No special lore recorded yet." }}

            === GRAND PLOT SUMMARY ===
            ${grandSummary.ifBlank { "Opening chapters of the narrative." }}

            === RECENT CONTEXT (LAST 3 CHAPTERS) ===
            ${recentChaptersText.ifBlank { "Beginning of the novel." }}

            === CURRENT TASK ===
            Write the full prose for:
            CHAPTER $chapterIndex: $cleanTitle
            Chapter objective & core events:
            $chapterObjective

            STRICT REQUIREMENTS:
            1. Jump directly into the prose. Do NOT include greetings or conversational filler.
            2. Maintain strict continuity with previously established events and character voices.
            3. Deliver rich, atmospheric, emotionally grounded, and immersive prose.
            """.trimIndent()
        }

        return Pair(systemPrompt, userPrompt)
    }

    /**
     * Parses generated outline text to extract a clean list of chapter titles.
     */
    fun extractChapterTitles(outlineText: String): List<String> {
        val titles = mutableListOf<String>()
        val lines = outlineText.lines()

        val pattern = Regex(
            """^(?:Chương|Hồi|Chapter|Part)\s+(\d+)\s*[:：.\-–—]\s*(.+)""",
            RegexOption.IGNORE_CASE
        )

        for (line in lines) {
            val match = pattern.find(line.trim())
            if (match != null) {
                val index = match.groupValues[1]
                val rawTitle = match.groupValues[2].trim()
                val cleanTitle = sanitizeChapterTitle(rawTitle)
                titles.add("Chương $index: $cleanTitle")
            }
        }

        return titles
    }
}
