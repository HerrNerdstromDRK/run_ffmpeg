package run_ffmpeg;

import com.google.gson.Gson;

public class JobRecord_MakeFakeOrTranscodeMKVFile
{
	public String mkvFileWithPath = null ;
	
	public JobRecord_MakeFakeOrTranscodeMKVFile( final String mkvFileWithPath )
	{
		this.mkvFileWithPath = mkvFileWithPath ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
