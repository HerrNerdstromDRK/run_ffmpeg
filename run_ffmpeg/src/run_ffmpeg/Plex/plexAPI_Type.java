package run_ffmpeg.Plex;
import java.util.List;

import com.google.gson.Gson;

public class plexAPI_Type
{
	public String key = null ;
	public String type = null ;
	public String subtype = null ;
	public List< plexAPI_Filter > Filter = null ;
	public List< plexAPI_Sort > Sort = null ;
	public List< plexAPI_Field > Field = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
