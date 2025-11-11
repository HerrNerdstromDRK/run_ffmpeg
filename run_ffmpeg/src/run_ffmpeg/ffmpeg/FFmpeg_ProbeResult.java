package run_ffmpeg.ffmpeg;

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
public class FFmpeg_ProbeResult
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
	public FFmpeg_Error error = null ;

	/// Store the format of the file
	public FFmpeg_Format format = null ;

	/// Store the streams
	public List< FFmpeg_Stream > streams = null ;

	/// The chapters
	public List< FFmpeg_Chapter > chapters = null ;
	
	/// Record if the file has subtitle streams that are too small to extract
	public Boolean smallSubtitleStreams = Boolean.FALSE ;

	/**
	 * Return a copy of the chapters.
	 * @return
	 */
	public List< FFmpeg_Chapter > getChapters()
	{
		if (chapters == null) return Collections.emptyList();
		return ImmutableList.copyOf(chapters);
	}

	public ObjectId get_id()
	{
		return _id;
	}

	public FFmpeg_Error getError()
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

	public FFmpeg_Format getFormat()
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
	public List< FFmpeg_Stream > getStreams()
	{
		if (streams == null) return Collections.emptyList();
		return ImmutableList.copyOf( streams );
	}

	/**
	 * Return a copy of each stream matching the given codec_type.
	 * @param searchType
	 * @return List of streams of the given type. Guaranteed to be non-null, but may be empty.
	 */
	public List< FFmpeg_Stream > getStreamsByCodecType( final String searchType )
	{
		assert( searchType != null ) ;
		assert( !searchType.isEmpty() ) ;
		assert( getStreams() != null ) ;
		
		List< FFmpeg_Stream > returnMe = new ArrayList< FFmpeg_Stream >() ;
		for( FFmpeg_Stream theInputStream : getStreams() )
		{
			if( theInputStream.codec_type.equalsIgnoreCase(searchType) )
			{
				returnMe.add( theInputStream ) ;
			}
		}		  
		return returnMe ;
	}
	
	/**
	 * This should work so long as exactly one video stream exists. I have, so far, not found a counterexample.
	 * @return
	 */
	public FFmpeg_Stream getVideoStream()
	{
		final List< FFmpeg_Stream > videoStreams = getStreamsByCodecType( "video" ) ;
		final FFmpeg_Stream videoStream = videoStreams.getFirst() ;
		return videoStream ;
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
		for( FFmpeg_Stream theStream : getStreams() )
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

	public boolean hasAudio()
	{
		assert( getStreams() != null ) ;
		final List< FFmpeg_Stream > audioStreams = getStreamsByCodecType( "audio" ) ;
		return !audioStreams.isEmpty() ;
	}
	
	public boolean hasError()
	{
		return error != null;
	}

	public boolean hasSubtitles()
	{
		assert( getStreams() != null ) ;
		
		final List< FFmpeg_Stream > subtitleStreams = getStreamsByCodecType( "subtitle" ) ;
		return !subtitleStreams.isEmpty() ;
	}
	
	public boolean isH264()
	{
		return getVideoCodec().equalsIgnoreCase( "h264" ) ;
	}

	public boolean isH265()
	{
		return getVideoCodec().equalsIgnoreCase( "h265" ) || getVideoCodec().equalsIgnoreCase( "hevc" ) ;
	}

	public boolean isMP2()
	{
		return getVideoCodec().equalsIgnoreCase( "mpeg2video" ) ;
	}

	public boolean isVC1()
	{
		return getVideoCodec().equalsIgnoreCase( "vc1" ) ;
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

	@Override
	public String toString()
	{
		Gson toStringGson = new Gson() ;
		final String objectToString = toStringGson.toJson( this ) ;
		return objectToString.toString() ;
	}
}
