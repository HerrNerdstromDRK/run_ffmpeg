package run_ffmpeg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;

import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;

/**
 * Store the data returned from an ffprobe invocation.
 * @author Dan
 */
public class FFmpegProbeResult
{
	/// This object's id in the database.
	public ObjectId _id ;

	/// The file being probed.
	public String fileNameWithPath = null ;
	public String fileNameWithoutPath = null ;
	public String fileNameShort = null ;

	/// The time, in ms since 1-Jan-1970, of this probe
	public long probeTime = 0 ;

	/// Size of the file in bytes
	public long size = 0 ;

	/// The time this file was last modified
	public long lastModified = 0 ;

	/// Store any error state
	public FFmpegError error = null ;

	/// Store the format of the file
	public FFmpegFormat format = null ;

	/// Store the streams
	public List< FFmpegStream > streams = null ;

	/// The chapters
	public List< FFmpegChapter > chapters = null ;
	
	/// Record if the file has subtitle streams that are too small to extract
	public Boolean smallSubtitleStreams = Boolean.FALSE ;

	/**
	 * Return a copy of the chapters.
	 * @return
	 */
	public List< FFmpegChapter > getChapters()
	{
		if (chapters == null) return Collections.emptyList();
		return ImmutableList.copyOf(chapters);
	}

	public ObjectId get_id()
	{
		return _id;
	}

	public FFmpegError getError()
	{
		return error;
	}

	public String getFileNameShort()
	{
		return fileNameShort;
	}

	public String getFileNameWithoutPath()
	{
		return fileNameWithoutPath;
	}

	public String getFileNameWithPath()
	{
		return fileNameWithPath;
	}

	public FFmpegFormat getFormat()
	{
		return format;
	}

	public long getLastModified()
	{
		return lastModified;
	}

	public long getProbeTime()
	{
		return probeTime;
	}

	public long getSize()
	{
		return size;
	}

	/**
	 * Return a copy of the streams.
	 * @return
	 */
	public List< FFmpegStream > getStreams()
	{
		if (streams == null) return Collections.emptyList();
		return ImmutableList.copyOf( streams );
	}

	/**
	 * Return a copy of each stream matching the given codec_type.
	 * @param searchType
	 * @return
	 */
	public List< FFmpegStream > getStreamsByCodecType( final String searchType )
	{
		assert( searchType != null ) ;
		assert( !searchType.isEmpty() ) ;
		assert( getStreams() != null ) ;
		
		List< FFmpegStream > returnMe = new ArrayList< FFmpegStream >() ;
		for( FFmpegStream theInputStream : getStreams() )
		{
			if( theInputStream.codec_type.equalsIgnoreCase(searchType) )
			{
				returnMe.add( theInputStream ) ;
			}
		}		  
		return returnMe ;
	}
	
	/**
	 * Return the video codec name. Known codecs are vc1, h264, hevc, mpeg2video. Note that the video stream is sometimes, although
	 *  not frequently, something other than stream 0.
	 * @return
	 */
	public String getVideoCodec()
	{
		if( (null == streams) || streams.isEmpty() )
		{
			return "" ;
		}
		
		String codecName = "" ;
		for( FFmpegStream theStream : getStreams() )
		{
			assert( theStream != null ) ;
			if( theStream.codec_type.equals( "video" ) )
			{
				codecName = theStream.codec_name ;
				break ;
			}
		}

		return codecName ;
	}

	public boolean hasError()
	{
		return error != null;
	}

	public boolean isMP2()
	{
		return getVideoCodec().equalsIgnoreCase( "mpeg2video" ) ;
	}
	
	public boolean isH264()
	{
		return getVideoCodec().equalsIgnoreCase( "h264" ) ;
	}
	
	public boolean isH265()
	{
		return getVideoCodec().equalsIgnoreCase( "h265" ) || getVideoCodec().equalsIgnoreCase( "hevc" ) ;
	}
	
	public boolean isVC1()
	{
		return getVideoCodec().equalsIgnoreCase( "vc1" ) ;
	}
	
	public boolean hasSubtitles()
	{
		assert( getStreams() != null ) ;
		
		final List< FFmpegStream > subtitleStreams = getStreamsByCodecType( "subtitle" ) ;
		return !subtitleStreams.isEmpty() ;
	}
	
	public void set_id( ObjectId _id )
	{
		this._id = _id;
	}

	public void setFileNameShort( String fileNameShort )
	{
		this.fileNameShort = fileNameShort;
	}

	public void setFileNameWithoutPath( String fileNameWithoutPath )
	{
		this.fileNameWithoutPath = fileNameWithoutPath;
	}

	public void setFileNameWithPath( String fileNameWithPath )
	{
		this.fileNameWithPath = fileNameWithPath;
	}

	public void setLastModified( long lastModified )
	{
		this.lastModified = lastModified;
	}

	public void setProbeTime( long probeTime )
	{
		this.probeTime = probeTime;
	}

	public void setSize( long size )
	{
		this.size = size;
	}
	
	public void setSmallSubtitleStreams( final boolean newValue )
	{
		this.smallSubtitleStreams = Boolean.valueOf( newValue ) ;
	}
	
	public boolean getSmallSubtitleStreams()
	{
		return smallSubtitleStreams.booleanValue() ;
	}

	public String toString()
	{
		Gson loginRequestGson = new Gson() ;
		final String loginRequestJson = loginRequestGson.toJson( this ) ;
		return loginRequestJson.toString() ;
	}
}
