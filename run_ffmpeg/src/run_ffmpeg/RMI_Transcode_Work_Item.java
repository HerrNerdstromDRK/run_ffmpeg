package run_ffmpeg;

import java.io.File;

import com.google.gson.Gson;

/**
 * This class is used to capture an individual unit of work for the RMI transcode subsystem.
 */
public class RMI_Transcode_Work_Item
{
	protected static final int NO_FRAME_NUMBER = -1 ;
	protected int startFrame = NO_FRAME_NUMBER ;
	protected int endFrame = NO_FRAME_NUMBER ;
	protected transient File inputFile = null ;
	protected transient File outputFile = null ;

	protected String segmentOutputFileNameWithPath = "" ;
	
	public RMI_Transcode_Work_Item( final File inputFile,
			final int startFrame,
			final int endFrame )
	{
		this.inputFile = inputFile ;
		this.startFrame = startFrame ;
		this.endFrame = endFrame ;
	}
	
	protected void setStartFrame( final int newFrameNumber )
	{
		startFrame = newFrameNumber ;
	}
	
	protected void setEndFrame( final int newFrameNumber )
	{
		endFrame = newFrameNumber ;
	}
	
	protected int getStartFrame()
	{
		return startFrame ;
	}
	
	protected int getEndFrame()
	{
		return endFrame ;
	}
	
	public String getSegmentFramesString()
	{
		String segmentFramesString = "" + getStartFrame() ;

		// An endFrame number of NO_FRAME_NUMBER simply means this is the last frame block for
		// the given file. In that case, the second frame number will be empty.
		if( getEndFrame() != NO_FRAME_NUMBER )
		{
			segmentFramesString += "," + getEndFrame() ;
		}
		return segmentFramesString ;
	}
	
	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}

	public String getSegmentOutputFileNameWithPath()
	{
		return segmentOutputFileNameWithPath ;
	}

	public void setSegmentOutputFileNameWithPath( String segmentOutputFileNameWithPath )
	{
		this.segmentOutputFileNameWithPath = segmentOutputFileNameWithPath ;
	}

	public File getInputFile()
	{
		return inputFile ;
	}

	public File getOutputFile()
	{
		return outputFile ;
	}

	public void setOutputFile( File outputFile )
	{
		this.outputFile = outputFile ;
	}
}
