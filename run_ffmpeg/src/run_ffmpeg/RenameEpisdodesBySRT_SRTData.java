package run_ffmpeg;

import java.io.File;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * A simple data storage class to store a reference to an srt file along with the first two minutes of its subtitle text.
 */
public class RenameEpisdodesBySRT_SRTData
{
	protected File srtFile = null ;
	protected String firstMinutesOfSubtitleText = null ;
	
	public RenameEpisdodesBySRT_SRTData( final File srtFile, final String firstMinutesOfSubtitleText )
	{
		super() ;
		this.srtFile = srtFile ;
		this.firstMinutesOfSubtitleText = firstMinutesOfSubtitleText ;
	}
	
	public File getSrtFile()
	{
		return srtFile ;
	}
	
	public void setSrtFile( final File srtFile )
	{
		this.srtFile = srtFile ;
	}
	
	public String getFirstMinutesOfSubtitleText()
	{
		return firstMinutesOfSubtitleText ;
	}
	
	public void setFirstTwoMinutesOfSubtitleText( final String firstMinutesOfSubtitleText )
	{
		this.firstMinutesOfSubtitleText = firstMinutesOfSubtitleText ;
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
