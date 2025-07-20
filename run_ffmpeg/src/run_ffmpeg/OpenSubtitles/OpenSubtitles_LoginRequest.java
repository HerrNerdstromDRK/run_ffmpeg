package run_ffmpeg.OpenSubtitles;

import com.google.gson.Gson;

public class OpenSubtitles_LoginRequest
{
	public String username = "" ;
	public String password = "" ;
	
	public OpenSubtitles_LoginRequest( final String username, final String password )
	{
		this.username = username ;
		this.password = password ;
	}
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
