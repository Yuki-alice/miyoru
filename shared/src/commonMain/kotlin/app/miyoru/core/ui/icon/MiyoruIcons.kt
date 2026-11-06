package app.miyoru.core.ui.icon

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 图标门面：屏幕层只引用语义名，不直接依赖 material-icons 的生成类。
 * 后续若要换 Material Symbols 或自绘图标，只改这里。
 */
object MiyoruIcons {
    val Home = Icons.Filled.Home
    val Library = Icons.Filled.MenuBook
    val History = Icons.Filled.History
    val Browse = Icons.Filled.Explore
    val More = Icons.Filled.MoreHoriz

    val Search = Icons.Filled.Search
    val Back = Icons.Filled.ArrowBack
    val MoreVert = Icons.Filled.MoreVert
    val Chevron = Icons.Filled.ChevronRight
    val Close = Icons.Filled.Close
    val Check = Icons.Filled.Check

    val Comic = Icons.Filled.AutoStories
    val Novel = Icons.Filled.Article
    val Video = Icons.Filled.Movie
    val Play = Icons.Filled.PlayArrow
    val PlayCircle = Icons.Filled.PlayCircle
    val Prev = Icons.Filled.SkipPrevious
    val Next = Icons.Filled.SkipNext
    val Speed = Icons.Filled.Speed
    val Fullscreen = Icons.Filled.Fullscreen
    val Chapters = Icons.Filled.List
    val TextStyle = Icons.Filled.TextFields

    val Source = Icons.Filled.Public
    val Plugin = Icons.Filled.Extension
    val Tune = Icons.Filled.Tune
    val Update = Icons.Filled.Update
    val Add = Icons.Filled.Add
    val BookmarkAdd = Icons.Filled.BookmarkAdd
    val Bell = Icons.Filled.Notifications

    val Download = Icons.Filled.Download
    val DownloadDone = Icons.Filled.DownloadDone
    val Pause = Icons.Filled.Pause
    val Clean = Icons.Filled.CleaningServices
    val DeleteSweep = Icons.Filled.DeleteSweep
    val Category = Icons.Filled.Category
    val Statistics = Icons.Filled.BarChart
    val Schedule = Icons.Filled.Schedule

    val Settings = Icons.Filled.Settings
    val Info = Icons.Filled.Info
    val Storage = Icons.Filled.Storage
    val Gavel = Icons.Filled.Gavel
    val Description = Icons.Filled.Description
    val Bug = Icons.Filled.BugReport
    val Folder = Icons.Filled.Folder
    val Image = Icons.Filled.Image
    val Backup = Icons.Filled.Backup
    val CloudUpload = Icons.Filled.CloudUpload
    val Restore = Icons.Filled.SettingsBackupRestore
    val DarkMode = Icons.Filled.DarkMode
    val Palette = Icons.Filled.Palette
    val Translate = Icons.Filled.Translate
    val SwapVert = Icons.Filled.SwapVert
    val Import = Icons.Filled.UploadFile
}
