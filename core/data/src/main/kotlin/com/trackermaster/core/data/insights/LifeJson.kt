package com.trackermaster.core.data.insights

import com.trackermaster.core.domain.insights.*
import com.trackermaster.core.domain.model.*
import org.json.*
import java.time.*

internal fun JSONArray.strings() = (0 until length()).map { getString(it) }
object LifeJson {
    fun goal(g: LifeGoal): String = JSONObject().apply {
        put("id",g.id);put("title",g.title);put("description",g.description);put("period",g.period.name)
        put("start",g.start);put("end",g.end);put("status",g.status);put("criterion",g.criterion)
        put("target",g.target);put("unit",g.unit);put("manual",g.manualProgress);put("source",g.source.name)
        put("links",JSONArray(g.linkedIds));put("notes",g.notes);put("parent",g.parentId);put("completedOn",g.completedOn)
        put("steps",JSONArray().apply { g.steps.forEach { put(JSONObject().put("title",it.title).put("done",it.completed)) } })
    }.toString()
    fun goal(raw: String): LifeGoal = JSONObject(raw).let { o ->
        LifeGoal(id=o.getString("id"),title=o.getString("title"),description=o.optString("description"),period=LifePeriod.valueOf(o.getString("period")),
            start=LocalDate.parse(o.getString("start")),end=LocalDate.parse(o.getString("end")),status=o.getString("status"),criterion=o.optString("criterion"),
            target=o.getDouble("target"),unit=o.optString("unit"),manualProgress=o.optDouble("manual",0.0),source=GoalSource.valueOf(o.getString("source")),
            linkedIds=o.getJSONArray("links").strings(),notes=o.optString("notes"),parentId=o.optString("parent").takeIf { it.isNotBlank() && it!="null" },
            completedOn=o.optString("completedOn").takeIf { it.isNotBlank() && it!="null" }?.let(LocalDate::parse),
            steps=o.getJSONArray("steps").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { SubTask(it.getString("title"),it.getBoolean("done")) } } })
    }
    fun journal(e: JournalEntry): String = JSONObject().apply {
        put("id",e.id);put("title",e.title);put("body",e.richTextHtml);put("created",e.createdAt);put("updated",e.updatedAt)
        put("mood",e.moodLevel);put("kind",e.kind);put("date",e.contentDate);put("tags",e.tags);put("emotions",e.emotions)
        put("lucid",e.lucid);put("habit",e.habitId);put("contribution",e.contribution);put("deleted",e.deletedAt)
        put("attachments",JSONArray().apply { e.attachments.forEach { put(JSONObject().put("path",it.path).put("mime",it.mimeType)) } })
    }.toString()
    fun journal(raw: String): JournalEntry = JSONObject(raw).let { o ->
        JournalEntry(id=o.getLong("id"),title=o.getString("title"),richTextHtml=o.getString("body"),createdAt=LocalDateTime.parse(o.getString("created")),
            updatedAt=LocalDateTime.parse(o.getString("updated")),moodLevel=if(o.isNull("mood")) null else o.getInt("mood"),kind=o.getString("kind"),
            contentDate=LocalDate.parse(o.getString("date")),tags=o.getString("tags"),emotions=o.getString("emotions"),lucid=o.getBoolean("lucid"),
            habitId=if(o.isNull("habit")) null else o.getLong("habit"),contribution=o.getInt("contribution"),deletedAt=if(o.isNull("deleted")) null else o.getLong("deleted"),
            attachments=o.getJSONArray("attachments").let { a -> (0 until a.length()).map { a.getJSONObject(it).let { Attachment(path=it.getString("path"),mimeType=it.getString("mime")) } } })
    }
}
