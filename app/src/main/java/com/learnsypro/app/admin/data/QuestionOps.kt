package com.learnsypro.app.admin.data

// Tương đương các handler onUp/onUpItem/onAddItem/onRemItem/onUpOpt/onAddOpt/onRemOpt
// trong app.jsx (upQ/upItem/addItem/remItem/upOpt/addOpt/remOpt) — thao tác thuần trên
// 1 Question, trả về bản copy mới. Dùng chung cho ViewModel gọi qua setQuestions().
object QuestionOps {

    fun updatePassage(q: Question.TrueFalse, value: String) = q.copy(passage = value)
    fun updateSource(q: Question.TrueFalse, value: String) = q.copy(source = value)

    fun updateItemText(q: Question.TrueFalse, index: Int, text: String): Question.TrueFalse =
        q.copy(items = q.items.mapIndexed { i, it -> if (i == index) it.copy(text = text) else it })

    fun updateItemAnswer(q: Question.TrueFalse, index: Int, answer: Boolean): Question.TrueFalse =
        q.copy(items = q.items.mapIndexed { i, it -> if (i == index) it.copy(answer = answer) else it })

    fun addItem(q: Question.TrueFalse): Question.TrueFalse =
        q.copy(items = q.items + TFItem("", true))

    // Tương đương q.items.length > 2 guard trước khi cho xoá
    fun removeItem(q: Question.TrueFalse, index: Int): Question.TrueFalse {
        if (q.items.size <= 2) return q
        return q.copy(items = q.items.filterIndexed { i, _ -> i != index })
    }

    fun updateQuestionText(q: Question.Multiple, value: String) = q.copy(question = value)
    fun updateOptionText(q: Question.Multiple, index: Int, value: String): Question.Multiple =
        q.copy(options = q.options.mapIndexed { i, o -> if (i == index) value else o })
    fun setCorrect(q: Question.Multiple, index: Int) = q.copy(correct = index)

    fun addOption(q: Question.Multiple): Question.Multiple {
        if (q.options.size >= 6) return q
        return q.copy(options = q.options + "")
    }

    fun removeOption(q: Question.Multiple, index: Int): Question.Multiple {
        if (q.options.size <= 2) return q
        val newOptions = q.options.filterIndexed { i, _ -> i != index }
        val newCorrect = when {
            q.correct == index -> 0
            q.correct > index -> q.correct - 1
            else -> q.correct
        }
        return q.copy(options = newOptions, correct = newCorrect)
    }

    fun updateQuestionText(q: Question.MultiSelect, value: String) = q.copy(question = value)
    fun updateOptionText(q: Question.MultiSelect, index: Int, value: String): Question.MultiSelect =
        q.copy(options = q.options.mapIndexed { i, o -> if (i == index) value else o })

    // Tương đương toggle trong mảng correct[] cho multi_select
    fun toggleCorrect(q: Question.MultiSelect, index: Int): Question.MultiSelect =
        q.copy(correct = if (index in q.correct) q.correct - index else q.correct + index)

    fun addOption(q: Question.MultiSelect): Question.MultiSelect {
        if (q.options.size >= 6) return q
        return q.copy(options = q.options + "")
    }

    fun removeOption(q: Question.MultiSelect, index: Int): Question.MultiSelect {
        if (q.options.size <= 2) return q
        val newOptions = q.options.filterIndexed { i, _ -> i != index }
        val newCorrect = q.correct.mapNotNull { c ->
            when {
                c == index -> null
                c > index -> c - 1
                else -> c
            }
        }
        return q.copy(options = newOptions, correct = newCorrect)
    }

    fun updateQuestionText(q: Question.FillBlank, value: String) = q.copy(question = value)
    fun updateAnswer(q: Question.FillBlank, value: String) = q.copy(answer = value)
    fun updateHint(q: Question.FillBlank, value: String) = q.copy(hint = value)
}
