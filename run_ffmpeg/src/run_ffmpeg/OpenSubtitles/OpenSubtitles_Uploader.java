package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_Uploader
{
	private Integer uploader_id;
	private String name;
	private String rank;

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getRank() {
		return rank;
	}

	public void setRank(String rank) {
		this.rank = rank;
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

	public Integer getUploader_id() {
		return uploader_id;
	}

	public void setUploader_id(Integer uploader_id) {
		this.uploader_id = uploader_id;
	}

}