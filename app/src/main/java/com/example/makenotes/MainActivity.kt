package com.example.makenotes

// Kotlin / Compose imports
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow

// Room imports
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Insert
import androidx.room.Update
import androidx.room.Delete

/* ---------------------------
   ROOM ENTITIES
   --------------------------- */

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "todos")
data class TodoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val text: String,
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

/* ---------------------------
   DAOs (Flow)
   --------------------------- */

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY createdAt DESC")
    fun getNotes(): Flow<List<NoteEntity>>

    @Insert
    suspend fun insert(note: NoteEntity)

    @Update
    suspend fun update(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Dao
interface TodoDao {
    @Query("SELECT * FROM todos ORDER BY createdAt DESC")
    fun getTodos(): Flow<List<TodoEntity>>

    @Insert
    suspend fun insert(todo: TodoEntity)

    @Update
    suspend fun update(todo: TodoEntity)

    @Delete
    suspend fun delete(todo: TodoEntity)
}

/* ---------------------------
   DATABASES (two separate DBs)
   --------------------------- */

@Database(entities = [NoteEntity::class], version = 1, exportSchema = false)
abstract class NotesDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        fun getInstance(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "notes_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

@Database(entities = [TodoEntity::class], version = 1, exportSchema = false)
abstract class TodosDatabase : RoomDatabase() {
    abstract fun todoDao(): TodoDao

    companion object {
        @Volatile
        private var INSTANCE: TodosDatabase? = null

        fun getInstance(context: Context): TodosDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    TodosDatabase::class.java,
                    "todos_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

/* ---------------------------
   REPOSITORIES
   --------------------------- */

class NoteRepository(private val dao: NoteDao) {
    val notes: Flow<List<NoteEntity>> = dao.getNotes()

    suspend fun insertNote(title: String, content: String) {
        dao.insert(NoteEntity(title = title, content = content))
    }

    suspend fun updateNote(entity: NoteEntity) {
        dao.update(entity)
    }

    suspend fun deleteNote(entity: NoteEntity) {
        dao.delete(entity)
    }
}

class TodoRepository(private val dao: TodoDao) {
    val todos: Flow<List<TodoEntity>> = dao.getTodos()

    suspend fun insertTodo(text: String) {
        dao.insert(TodoEntity(text = text))
    }

    suspend fun updateTodo(entity: TodoEntity) {
        dao.update(entity)
    }

    suspend fun deleteTodo(entity: TodoEntity) {
        dao.delete(entity)
    }
}

/* ---------------------------
   UI MODELS
   --------------------------- */

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val createdAt: Long
)

data class Todo(
    val id: Long,
    val text: String,
    val done: Boolean,
    val createdAt: Long
)

/* ---------------------------
   THEME & COLORS
   --------------------------- */

private val YellowAccent = Color(0xFFFFD600)
private val BackgroundDark = Color(0xFFFFFFFF)
private val SurfaceDark = Color(0xFFD2D2D2)
private val CardDark = Color(0xFFF6F6F6)
private val TextColor = Color(0xFF000000)


@Composable
fun NotesAppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = YellowAccent,
            background = BackgroundDark,
            surface = SurfaceDark,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        typography = Typography(),
        content = content
    )
}

/* ---------------------------
   NAV SCREENS (including EditNote)
   --------------------------- */

sealed class Screen {
    object NotesList : Screen()
    object TodoList : Screen()
    object CreateNote : Screen()
    object CreateTodo : Screen()
    object Settings : Screen()
    data class EditNote(val note: Note) : Screen()
}

/* ---------------------------
   MAIN ACTIVITY (single file)
   --------------------------- */

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val notesDb = NotesDatabase.getInstance(applicationContext)
        val todosDb = TodosDatabase.getInstance(applicationContext)

        val noteRepo = NoteRepository(notesDb.noteDao())
        val todoRepo = TodoRepository(todosDb.todoDao())

        setContent {
            AppRoot(noteRepo = noteRepo, todoRepo = todoRepo)
        }
    }
}

/* ---------------------------
   APP ROOT
   --------------------------- */

@Composable
fun AppRoot(noteRepo: NoteRepository, todoRepo: TodoRepository) {
    var screen by remember { mutableStateOf<Screen>(Screen.NotesList) }
    val scope = rememberCoroutineScope()

    // collect entity flows and map to UI models
    val noteEntities by noteRepo.notes.collectAsState(initial = emptyList())
    val notes by remember(noteEntities) {
        derivedStateOf { noteEntities.map { e -> Note(e.id, e.title, e.content, e.createdAt) } }
    }

    val todoEntities by todoRepo.todos.collectAsState(initial = emptyList())
    val todos by remember(todoEntities) {
        derivedStateOf { todoEntities.map { t -> Todo(t.id, t.text, t.done, t.createdAt) } }
    }

    NotesAppTheme {
        Scaffold(
            topBar = { TopBar(onSettings = { screen = Screen.Settings }) },
            containerColor = MaterialTheme.colorScheme.background,
            floatingActionButton = {
                when (screen) {
                    is Screen.NotesList -> FloatingActionButton(
                        onClick = { screen = Screen.CreateNote },
                        containerColor = YellowAccent
                    ) { Icon(Icons.Default.Add, contentDescription = null) }

                    is Screen.TodoList -> FloatingActionButton(
                        onClick = { screen = Screen.CreateTodo },
                        containerColor = YellowAccent
                    ) { Icon(Icons.Default.Add, contentDescription = null) }

                    else -> Unit
                }
            },
            bottomBar = {
                BottomNav(selected = if (screen is Screen.TodoList) 1 else 0) { idx ->
                    screen = if (idx == 0) Screen.NotesList else Screen.TodoList
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding).fillMaxSize()) {

                // Notes list & edit
                AnimatedVisibility(screen is Screen.NotesList, enter = fadeIn(), exit = fadeOut()) {
                    NotesListScreen(
                        notes = notes,
                        onDelete = { note ->
                            scope.launch {
                                val entity = noteEntities.firstOrNull { it.id == note.id }
                                if (entity != null) noteRepo.deleteNote(entity)
                            }
                        },
                        onClick = { note ->
                            screen = Screen.EditNote(note)
                        }
                    )
                }

                // Todos
                AnimatedVisibility(screen is Screen.TodoList, enter = fadeIn(), exit = fadeOut()) {
                    TodoListScreen(
                        todos = todos,
                        onToggle = { t ->
                            scope.launch {
                                val entity = todoEntities.firstOrNull { it.id == t.id }
                                if (entity != null) todoRepo.updateTodo(entity.copy(done = !entity.done))
                            }
                        },
                        onDelete = { t ->
                            scope.launch {
                                val entity = todoEntities.firstOrNull { it.id == t.id }
                                if (entity != null) todoRepo.deleteTodo(entity)
                            }
                        }
                    )
                }

                // Settings
                AnimatedVisibility(visible = screen is Screen.Settings, enter = fadeIn(), exit = fadeOut()) {
                    SettingsScreen(onBack = { screen = Screen.NotesList })
                }

                // Create Note
                AnimatedVisibility(screen is Screen.CreateNote, enter = fadeIn(), exit = fadeOut()) {
                    CreateNoteScreen(
                        onCancel = { screen = Screen.NotesList },
                        onSave = { title, content ->
                            scope.launch {
                                noteRepo.insertNote(title, content)
                                screen = Screen.NotesList
                            }
                        }
                    )
                }

                // Create Todo
                AnimatedVisibility(screen is Screen.CreateTodo, enter = fadeIn(), exit = fadeOut()) {
                    CreateTodoScreen(
                        onCancel = { screen = Screen.TodoList },
                        onSave = { text ->
                            scope.launch {
                                todoRepo.insertTodo(text)
                                screen = Screen.TodoList
                            }
                        }
                    )
                }

                // Edit note screen
                AnimatedVisibility(screen is Screen.EditNote, enter = fadeIn(), exit = fadeOut()) {
                    val s = screen
                    if (s is Screen.EditNote) {
                        EditNoteScreen(
                            note = s.note,
                            onSave = { newTitle, newContent ->
                                scope.launch {
                                    // find entity and update
                                    val entity = noteEntities.firstOrNull { it.id == s.note.id }
                                    if (entity != null) {
                                        noteRepo.updateNote(entity.copy(title = newTitle, content = newContent))
                                    }
                                    screen = Screen.NotesList
                                }
                            },
                            onCancel = {
                                screen = Screen.NotesList
                            }
                        )
                    }
                }
            }
        }
    }
}

/* ---------------------------
   TOP BAR
   --------------------------- */

@Composable
fun TopBar(onSettings: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 18.dp, horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notes", fontSize = 36.sp, fontWeight = FontWeight.Bold ,color = TextColor)
                Spacer(Modifier.width(6.dp))
            }

        }
    }
}

/* ---------------------------
   BOTTOM NAV
   --------------------------- */

@Composable
fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    NavigationBar(containerColor = SurfaceDark) {
        NavigationBarItem(selected = selected == 0, onClick = { onSelect(0) }, icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Menu, contentDescription = null, tint = if (selected == 0) YellowAccent else Color.Black)
                Text("Notes", color = if (selected == 0) YellowAccent else Color.Black, fontSize = 12.sp)
            }
        }, alwaysShowLabel = false)

        NavigationBarItem(selected = selected == 1, onClick = { onSelect(1) }, icon = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Check, contentDescription = null, tint = if (selected == 1) YellowAccent else Color.Black)
                Text("To-Do", color = if (selected == 1) YellowAccent else Color.Black, fontSize = 12.sp)
            }
        }, alwaysShowLabel = false)
    }
}

/* ---------------------------
   NOTES LIST UI (clickable item -> edit)
   --------------------------- */

@Composable
fun NotesListScreen(notes: List<Note>, onDelete: (Note) -> Unit, onClick: (Note) -> Unit) {
    if (notes.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No notes yet. Tap + to create one.", color = Color.Black)
        }
        return
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 18.dp)) {
        items(notes) { note ->
            Card(modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick(note) }
                .clip(RoundedCornerShape(14.dp)), colors = CardDefaults.cardColors(containerColor = CardDark)) {
                Column(Modifier.padding(18.dp)) {
                    Text(java.text.SimpleDateFormat("dd/MM/yyyy").format(java.util.Date(note.createdAt)), fontSize = 12.sp, color = Color.Black)
                    Spacer(Modifier.height(8.dp))
                    Text(note.title, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.Black)
                    if (note.content.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(note.content, fontSize = 13.sp, color = Color.Black, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("Delete", color = Color.Red, modifier = Modifier.clickable { onDelete(note) })
                    }
                }
            }
        }
    }
}

/* ---------------------------
   TODO LIST UI
   --------------------------- */
@Composable
fun TodoListScreen(
    todos: List<Todo>,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit
) {
    val notCompleted = todos.filter { !it.done }
    val completed = todos.filter { it.done }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 18.dp)
    ) {

        /* ---------- NOT COMPLETED SECTION ---------- */
        item {
            if (notCompleted.isNotEmpty()) {
                Text(
                    "Not Completed",
                    color = Color.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        items(notCompleted) { todo ->
            TodoItem(todo = todo, onToggle = onToggle, onDelete = onDelete)
        }

        /* ---------- COMPLETED SECTION ---------- */
        item {
            if (completed.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    "Completed",
                    color = Color.Black,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
        }

        items(completed) { todo ->
            TodoItem(todo = todo, onToggle = onToggle, onDelete = onDelete)
        }
    }
}

/* ---------------------------
   CREATE NOTE UI
   --------------------------- */

@Composable
fun CreateNoteScreen(onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var content by remember { mutableStateOf(TextFieldValue("")) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("New Note", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        BasicTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardDark).padding(12.dp), decorationBox = { inner ->
            if (title.text.isEmpty()) Text("Title", color = Color.Gray)
            inner()
        })

        Spacer(Modifier.height(12.dp))

        BasicTextField(value = content, onValueChange = { content = it }, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardDark).padding(12.dp), decorationBox = { inner ->
            if (content.text.isEmpty()) Text("Content", color = Color.Gray)
            inner()
        })

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (title.text.isNotBlank()) onSave(title.text, content.text)
            }) {
                Text("Save")
            }
        }
    }
}

/* ---------------------------
   CREATE TODO UI
   --------------------------- */

@Composable
fun CreateTodoScreen(onCancel: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(TextFieldValue("")) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("New Todo", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        BasicTextField(value = text, onValueChange = { text = it }, singleLine = true, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardDark).padding(12.dp), decorationBox = { inner ->
            if (text.text.isEmpty()) Text("Todo text", color = Color.Gray)
            inner()
        })

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (text.text.isNotBlank()) onSave(text.text)
            }) {
                Text("Save")
            }
        }
    }
}

/* ---------------------------
   EDIT NOTE UI
   --------------------------- */

@Composable
fun EditNoteScreen(onCancel: () -> Unit = {}, onSave: (String, String) -> Unit, note: Note) {
    var title by remember { mutableStateOf(TextFieldValue(note.title)) }
    var content by remember { mutableStateOf(TextFieldValue(note.content)) }

    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp).clickable { onCancel() })
            Spacer(Modifier.width(12.dp))
            Text("Edit Note", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        BasicTextField(value = title, onValueChange = { title = it }, singleLine = true, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardDark).padding(12.dp), decorationBox = { inner ->
            if (title.text.isEmpty()) Text("Title", color = Color.Gray)
            inner()
        })

        Spacer(Modifier.height(12.dp))

        BasicTextField(value = content, onValueChange = { content = it }, modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(CardDark).padding(12.dp), decorationBox = { inner ->
            if (content.text.isEmpty()) Text("Content", color = Color.Gray)
            inner()
        })

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onCancel() }) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(onClick = {
                if (title.text.isNotBlank()) onSave(title.text, content.text)
            }) {
                Text("Save")
            }
        }
    }
}

/* ---------------------------
   SETTINGS UI
   --------------------------- */

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp).clickable { onBack() })
            Spacer(Modifier.width(12.dp))
            Text("Settings", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Spacer(Modifier.height(24.dp))

        SettingItem("Dark Mode")
        SettingItem("Notifications")
        SettingItem("Backup & Restore")
        SettingItem("About App")
    }
}

@Composable
fun SettingItem(title: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(12.dp)).background(CardDark).padding(16.dp)) {
        Text(title, fontSize = 18.sp, color = Color.White)
    }
}
@Composable
fun TodoItem(
    todo: Todo,
    onToggle: (Todo) -> Unit,
    onDelete: (Todo) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(containerColor = CardDark)
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = todo.done,
                onCheckedChange = { onToggle(todo) }
            )

            Spacer(Modifier.width(12.dp))

            Text(
                todo.text,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                color = if (todo.done) Color.Black else Color.Black
            )

            Text(
                "Delete",
                color = Color.Red,
                modifier = Modifier.clickable { onDelete(todo) }
            )
        }
    }
}
