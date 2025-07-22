package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class OpenSubtitles_DownloadResponse
{
	public String link = "" ;
	public String file_name = "" ;
	public Integer reqeusts = null ;
	public Integer remaining = null ;
	public String message = "" ;
	public String reset_time = "" ;
	public String reset_time_utc = "" ;	
	
	@Override
	public String toString()
	{
		GsonBuilder builder = new GsonBuilder() ; 
		builder.setPrettyPrinting() ; 
		Gson gson = builder.create() ;
		final String json = gson.toJson( this ) ;
		return json ;
	}

	public String getLink() {
		return link;
	}

	public void setLink(String link) {
		this.link = link;
	}

	public String getFile_name() {
		return file_name;
	}

	public void setFile_name(String file_name) {
		this.file_name = file_name;
	}

	public Integer getReqeusts() {
		return reqeusts;
	}

	public void setReqeusts(Integer reqeusts) {
		this.reqeusts = reqeusts;
	}

	public Integer getRemaining() {
		return remaining;
	}

	public void setRemaining(Integer remaining) {
		this.remaining = remaining;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public String getReset_time() {
		return reset_time;
	}

	public void setReset_time(String reset_time) {
		this.reset_time = reset_time;
	}

	public String getReset_time_utc() {
		return reset_time_utc;
	}

	public void setReset_time_utc(String reset_time_utc) {
		this.reset_time_utc = reset_time_utc;
	}
}
