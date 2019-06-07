package at.dibiasi.funker.obex

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JacksonXmlRootElement(localName = "folder-listing")
class Folderlisting {
    @JacksonXmlProperty(localName = "file")
    @JacksonXmlElementWrapper(useWrapping = false)
    private val files: ArrayList<File> = arrayListOf()

    @JacksonXmlProperty(localName = "folder")
    @JacksonXmlElementWrapper(useWrapping = false)
    private val folders: ArrayList<Folder> = arrayListOf()

    @JsonProperty("version")
    val version: String? = null


    fun setFolders(values: List<Folder>) {
        folders.addAll(values)
    }

    fun setFiles(values: List<File>) {
        files.addAll(values)
    }

    override fun toString(): String {
        return "Folder listing: ${folders.size} folders, ${files.size} files, Version $version"
    }
}

@JacksonXmlRootElement(localName = "folder")
data class Folder(
    @JsonProperty("name")
    val name: String?,
    @JsonProperty("size")
    val size: String?,
    @JsonProperty("user-perm")
    val userperm: String?,
    @JsonProperty("group-perm")
    val groupperm: String?,
    @JsonProperty("other-perm")
    val otherperm: String?,
    @JsonProperty("accessed")
    val accessed: String?,
    @JsonProperty("modified")
    val modified: String?,
    @JsonProperty("created")
    val created: String?
)

@JacksonXmlRootElement(localName = "file")
data class File(
    @JsonProperty("name")
    val name: String?,
    @JsonProperty("size")
    val size: String?,
    @JsonProperty("user-perm")
    val userperm: String?,
    @JsonProperty("group-perm")
    val groupperm: String?,
    @JsonProperty("other-perm")
    val otherperm: String?,
    @JsonProperty("accessed")
    val accessed: String?,
    @JsonProperty("modified")
    val modified: String?,
    @JsonProperty("created")
    val created: String?
)


