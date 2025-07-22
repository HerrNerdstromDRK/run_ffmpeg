package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_File
{
	private Integer file_id;
	private Integer cd_number;
	private String file_name ;

	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ; 
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

	public Integer getFile_id() {
		return file_id;
	}

	public void setFile_id(Integer file_id) {
		this.file_id = file_id;
	}

	public Integer getCd_number() {
		return cd_number;
	}

	public void setCd_number(Integer cd_number) {
		this.cd_number = cd_number;
	}

	public String getFile_name() {
		return file_name;
	}

	public void setFile_name(String file_name) {
		this.file_name = file_name;
	}
}