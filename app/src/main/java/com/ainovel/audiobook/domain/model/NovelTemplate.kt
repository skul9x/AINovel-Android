package com.ainovel.audiobook.domain.model

data class NovelTemplate(
    val id: String,
    val nameVi: String,
    val nameEn: String,
    val descriptionVi: String,
    val descriptionEn: String,
    val systemPromptVi: String,
    val systemPromptEn: String
) {
    companion object {
        val WEB_NOVEL = NovelTemplate(
            id = "web_novel",
            nameVi = "Tiểu Thuyết Mạng (Sảng Văn)",
            nameEn = "Web Novel (Progression Fantasy)",
            descriptionVi = "Nhịp độ nhanh, kịch tính dồn dập, cao trào liên tiếp, nhân vật chính thông minh quyết đoán.",
            descriptionEn = "Fast-paced, high tension, satisfying progression, decisive and witty protagonist.",
            systemPromptVi = "Bạn là đại thần tiểu thuyết mạng. Văn phong lưu loát, dồn dập, phân đoạn ngắn, tạo cliffhanger hấp dẫn ở cuối mỗi chương.",
            systemPromptEn = "You are a master web novel author. Vivid pacing, addictive progression arcs, short paragraphs, strong cliffhangers."
        )

        val LITERARY = NovelTemplate(
            id = "literary",
            nameVi = "Văn Học Nghệ Thuật / Chiêm Nghiệm",
            nameEn = "Literary Fiction",
            descriptionVi = "Văn phong trau chuốt, giàu tính biểu tượng, đào sâu thế giới nội tâm và triết lý nhân sinh.",
            descriptionEn = "Rich prose, symbolic imagery, deep character psychology, philosophical depth.",
            systemPromptVi = "Bạn là nhà văn đoạt giải văn học. Hãy sử dụng câu từ giàu chất thơ, tinh tế, khắc họa chiều sâu tâm lý nhân vật và bối cảnh chân thực.",
            systemPromptEn = "You are an acclaimed literary novelist. Craft atmospheric, emotionally resonant, and psychologically intricate prose."
        )

        val HARD_SCI_FI = NovelTemplate(
            id = "hard_sci_fi",
            nameVi = "Khoa Học Viễn Tưởng (Hard Sci-Fi)",
            nameEn = "Hard Sci-Fi & Cyberpunk",
            descriptionVi = "Logic công nghệ chặt chẽ, thế giới quan viễn tưởng đồ sộ, thuyết phục và nghẹt thở.",
            descriptionEn = "Rigorous scientific logic, expansive cyberpunk / space-opera worldbuilding.",
            systemPromptVi = "Bạn là tác giả khoa học viễn tưởng theo trường phái cứng (Hard Sci-Fi). Hãy mô tả công nghệ, vũ trụ và xã hội tương lai với tính logic và chi tiết cơ học sắc bén.",
            systemPromptEn = "You are a hard science fiction author. Emphasize scientific plausibility, technological detail, and awe-inspiring worldbuilding."
        )

        val ROMANCE = NovelTemplate(
            id = "romance",
            nameVi = "Ngôn Tình / Lãng Mạn",
            nameEn = "Romance & Drama",
            descriptionVi = "Cảm xúc ngọt ngào, tinh tế, đối thoại tự nhiên, khắc họa rung động và sự gắn kết tâm hồn.",
            descriptionEn = "Emotional resonance, natural chemistry, witty dialogue, heartwarming relationship dynamics.",
            systemPromptVi = "Bạn là tác giả tiểu thuyết lãng mạn. Hãy miêu tả tinh tế những chuyển biến tâm trạng, ánh mắt, cử chỉ và xúc cảm chân thật giữa các nhân vật.",
            systemPromptEn = "You are an experienced romance novelist. Capture delicate nuances of chemistry, witty banter, and deep emotional stakes."
        )

        val MYSTERY = NovelTemplate(
            id = "mystery",
            nameVi = "Trinh Thám / Ly Kỳ",
            nameEn = "Mystery & Thriller",
            descriptionVi = "Logic suy luận chặt chẽ, bầu không khí hồi hộp, manh mối cài cắm tinh vi và cú lật bất ngờ.",
            descriptionEn = "Tight deductive logic, suspenseful atmosphere, subtly planted clues, and shocking twists.",
            systemPromptVi = "Bạn là nhà văn trinh thám bậc thầy. Hãy xây dựng vụ án ly kỳ, đối thoại sắc sảo, cài cắm manh mối logic và giữ vững bí ẩn cho đến phút chót.",
            systemPromptEn = "You are a master thriller and mystery writer. Build suffocating suspense, intricate deductive reasoning, and jaw-dropping twists."
        )

        val CUSTOM = NovelTemplate(
            id = "custom",
            nameVi = "Tùy Chỉnh (Giáo trình & Tác phẩm tự do)",
            nameEn = "Custom / Courseware & Freeform",
            descriptionVi = "Thiết kế cấu trúc chương hồi tự do, phù hợp cả viết sách học thuật lẫn kịch bản đặc thù.",
            descriptionEn = "Flexible outlining and structural design suitable for curriculum, essays, and custom projects.",
            systemPromptVi = "Bạn là chuyên gia biên soạn nội dung hàng đầu. Trình bày khúc chiết, mạch lạc, dễ hiểu và đáp ứng chính xác mọi yêu cầu của đề tài.",
            systemPromptEn = "You are a premier educational and technical author. Deliver lucid, logically structured, and deeply informative content."
        )

        val ALL_TEMPLATES = listOf(WEB_NOVEL, LITERARY, HARD_SCI_FI, ROMANCE, MYSTERY, CUSTOM)

        fun findById(id: String): NovelTemplate {
            return ALL_TEMPLATES.find { it.id.equals(id, ignoreCase = true) } ?: WEB_NOVEL
        }
    }
}
