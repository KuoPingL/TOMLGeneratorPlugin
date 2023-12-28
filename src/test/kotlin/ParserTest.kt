import com.bluewhale.tomlgen.Parser
import org.junit.Test
import org.junit.runners.JUnit4

class ParserTest {

    @Test
    fun `parse dependencies scope with nothing`() {
        val line = "dependencies {  }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.isEmpty())
    }

    @Test
    fun `parse dependencies scope with non-dependencies scope`() {
        val line = "dependency {  }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.isEmpty())
    }

    @Test
    fun `parse dependencies scope with group name version dep`() {
        val line = "dependencies { config(\"group:name:1.1.1\") }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.size == 1)
        assert(result.first().config == "config")
        assert(result.first().group == "group")
        assert(result.first().name == "name")
        assert(result.first().version == "1.1.1")
    }

    @Test
    fun `parse dependencies scope with group name version with equal dep`() {
        val line = "dependencies { config(group = \"group\", name = \"name\", version=\"1.1.1\") }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.size == 1)
        assert(result.first().config == "config")
        assert(result.first().group == "group")
        assert(result.first().name == "name")
        assert(result.first().version == "1.1.1")
    }

    @Test
    fun `parse dependencies scope with group name incorrect version with equal dep`() {
        val line = "dependencies { config(group = \"group\", name = \"name\", version=\"1-1\") }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.isEmpty())
    }

    @Test
    fun `parse dependencies scope with group name dashed version dep`() {
        val line = "dependencies { config(\"group:name:1-1\") }"
        val result = Parser.extractDependenciesFrom(line)
        assert(result.isEmpty())
    }

    @Test
    fun `parse group name dashed version dep`() {
        val line = "config(\"group:name:1-1\")"
        val result = Parser.extractGroupNameVersionWithSemiColon(line, "config", line)
        assert(result == null)
    }
}