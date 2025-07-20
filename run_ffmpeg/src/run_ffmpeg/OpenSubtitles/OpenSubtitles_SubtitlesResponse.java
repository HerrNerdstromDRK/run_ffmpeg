package run_ffmpeg.OpenSubtitles;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_SubtitlesResponse
{
	private Integer totalPages;
	private Integer totalCount;
	private Integer perPage;
	private Integer page;
	private List<OpenSubtitles_Data> data;
	private Map<String, Object> additionalProperties = new LinkedHashMap<String, Object>();

	public Integer getTotalPages() {
		return totalPages;
	}

	public void setTotalPages(Integer totalPages) {
		this.totalPages = totalPages;
	}

	public Integer getTotalCount() {
		return totalCount;
	}

	public void setTotalCount(Integer totalCount) {
		this.totalCount = totalCount;
	}

	public Integer getPerPage() {
		return perPage;
	}

	public void setPerPage(Integer perPage) {
		this.perPage = perPage;
	}

	public Integer getPage() {
		return page;
	}

	public void setPage(Integer page) {
		this.page = page;
	}

	public List<OpenSubtitles_Data> getData() {
		return data;
	}

	public void setData(List<OpenSubtitles_Data> data) {
		this.data = data;
	}

	public Map<String, Object> getAdditionalProperties() {
		return this.additionalProperties;
	}

	public void setAdditionalProperty(String name, Object value) {
		this.additionalProperties.put(name, value);
	}

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ;
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

}