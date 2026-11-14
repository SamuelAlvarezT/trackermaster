package com.trackermaster.feature.journal

import org.junit.Assert.*
import org.junit.Test

class DiaryImportTest {
    @Test fun separatesEntriesOnSameDateAndPreservesParagraphs() {
        val text = "\n2023/05/03\n\nÁrbol <casa> & sol\n\nOtro párrafo\n\nPowered by MyDiary\n-----\n\n2023/05/03\n\nSegundo\n\nPowered by MyDiary\n-----\n"
        val rows = parseMyDiary(text)
        assertEquals(2, rows.size)
        assertEquals(rows[0].date, rows[1].date)
        assertEquals("Árbol <casa> & sol\n\nOtro párrafo\n", rows[0].body)
        assertEquals(rows[0].body, stripHtml(encodeImportedText(rows[0].body)))
    }
    @Test fun refusesTrailingUnparsedContent() {
        assertThrows(IllegalArgumentException::class.java) { parseMyDiary("2023/05/03\n\ntexto\nPowered by MyDiary\n-----\nperdido") }
    }
}
