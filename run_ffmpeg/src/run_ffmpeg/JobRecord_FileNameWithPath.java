package run_ffmpeg;

import java.io.File;

import com.google.gson.Gson;

public class JobRecord_FileNameWithPath
{
	public String fileNameWithPath = null ;
	
	/// Default public constructor mandatory for serialization.
	public JobRecord_FileNameWithPath()
	{}
	
	public JobRecord_FileNameWithPath( final String fileNameWithPath )
	{
		this.fileNameWithPath = fileNameWithPath ;
	}
	
	public JobRecord_FileNameWithPath( final File inputFile )
	{
		this.fileNameWithPath = inputFile.getAbsolutePath() ;
	}

	public String toString()
	{
		Gson gsonRequest= new Gson() ;
		final String jsonRequest = gsonRequest.toJson( this ) ;
		return jsonRequest.toString() ;
	}

	public String getFileNameWithPath()
	{
		return fileNameWithPath ;
	}

	public void setFileNameWithPath( final String fileNameWithPath )
	{
		this.fileNameWithPath = fileNameWithPath ;
	}
}
