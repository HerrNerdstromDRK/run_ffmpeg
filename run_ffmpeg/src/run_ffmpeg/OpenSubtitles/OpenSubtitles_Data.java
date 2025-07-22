package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_Data
{
	private String id;
	private String type;
	private OpenSubtitles_Attributes attributes;

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public OpenSubtitles_Attributes getAttributes() {
		return attributes;
	}

	public void setAttributes(OpenSubtitles_Attributes attributes) {
		this.attributes = attributes;
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
