import re

filepath = r'c:\Users\Hama9\Desktop\kkkkk\master\app\src\main\java\com\optic\tv\PlaylistActivity.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    text = f.read()

# Add imports for Room and Coroutines
import_block = '''import com.optic.tv.db.AppDatabase
import com.optic.tv.db.ChannelEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
'''
text = text.replace('import com.optic.tv.models.PlaylistData', 'import com.optic.tv.models.PlaylistData\n' + import_block)

text = re.sub(r'groupMap\.clear\(\)', 'groupMap.clear()\n                val batch = mutableListOf<ChannelEntity>()\n                runBlocking(Dispatchers.IO) { AppDatabase.getDatabase(this@PlaylistActivity).channelsDao().clearAll() }', text, count=1)

old_parse = r'''                                val channel = ChannelsData\(
                                    name = currentName,
                                    logo = currentLogo,
                                    url = trimmed,
                                    userAgent = currentUserAgent,
                                    referrer = currentReferrer
                                \)
                                val key = "\\|\"
                                groupMap.getOrPut\(key\) \{ mutableListOf\(\) \}.add\(channel\)'''

new_parse = '''                                val channel = ChannelEntity(
                                    name = currentName,
                                    logo = currentLogo,
                                    url = trimmed,
                                    userAgent = currentUserAgent,
                                    referrer = currentReferrer,
                                    type = resolvedType,
                                    groupName = currentGroup
                                )
                                batch.add(channel)
                                if (batch.size >= 2500) {
                                    val flush = ArrayList(batch)
                                    batch.clear()
                                    runBlocking(Dispatchers.IO) { AppDatabase.getDatabase(this@PlaylistActivity).channelsDao().insertChannels(flush) }
                                }
                                channelsFound++
                                currentName = ""
                                continue'''

# Let's cheat. Instead of deleting groupMap, we just populate the Room DB simultaneously! 
# That way SharedPreferences isn't needed anymore, and we have room DB ready.
text = re.sub(old_parse, old_parse.replace('groupMap.getOrPut(key) { mutableListOf() }.add(channel)', 'groupMap.getOrPut(key) { mutableListOf() }.add(channel)\n' + new_parse.split('groupName = currentGroup\n                                )\n')[1]), text)

with open(filepath, 'w', encoding='utf-8') as f:
    f.write(text)

print('Done')
