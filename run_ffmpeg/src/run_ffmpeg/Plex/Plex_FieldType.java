package run_ffmpeg.Plex;

import java.util.List;

import com.google.gson.Gson;

public class Plex_FieldType
{
	public String type = null ;
	public List< Plex_Operator > Operator = null ;
	
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
