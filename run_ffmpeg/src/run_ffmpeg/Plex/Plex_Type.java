package run_ffmpeg.Plex;
import java.util.List;

import com.google.gson.Gson;

public class Plex_Type
{
	public String key = null ;
	public String type = null ;
	public String subtype = null ;
	public List< Plex_Filter > Filter = null ;
	public List< Plex_Sort > Sort = null ;
	public List< Plex_Field > Field = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
