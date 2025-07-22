package run_ffmpeg.OpenSubtitles;

import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_SubtitlesResponse
{
	private Integer total_pages;
	private Integer total_count;
	private Integer per_page;
	private Integer page;
	private List<OpenSubtitles_Data> data;

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

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ;
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

	public Integer getTotal_pages() {
		return total_pages;
	}

	public void setTotal_pages(Integer total_pages) {
		this.total_pages = total_pages;
	}

	public Integer getTotal_count() {
		return total_count;
	}

	public void setTotal_count(Integer total_count) {
		this.total_count = total_count;
	}

	public Integer getPer_page() {
		return per_page;
	}

	public void setPer_page(Integer per_page) {
		this.per_page = per_page;
	}

}