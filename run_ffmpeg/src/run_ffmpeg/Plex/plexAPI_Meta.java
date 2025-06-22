package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class plexAPI_Meta
{
	public List< plexAPI_Type > Type = null ;
	public List< plexAPI_FieldType > FieldType = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
