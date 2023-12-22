package run_ffmpeg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.bson.types.ObjectId;

import com.google.common.collect.ImmutableList;

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

	public boolean hasError()
	{
		return error != null;
	}

	public void set_id( ObjectId _id )
	{
		this._id = _id;
	}

	public void setFileNameShort(String fileNameShort)
	{
		this.fileNameShort = fileNameShort;
	}

	public void setFileNameWithoutPath(String fileNameWithoutPath)
	{
		this.fileNameWithoutPath = fileNameWithoutPath;
	}

	public void setFileNameWithPath(String fileNameWithPath)
	{
		this.fileNameWithPath = fileNameWithPath;
	}

	public void setLastModified(long lastModified)
	{
		this.lastModified = lastModified;
	}

	public void setProbeTime(long probeTime)
	{
		this.probeTime = probeTime;
	}

	public void setSize(long size)
	{
		this.size = size;
	}

	public String toString()
	{
		String retMe =	"{"
				+ "id: " + ((get_id() != null) ? get_id().toString() : "(null)")
				+ ", fileNameWithPath: " + ((getFileNameWithPath() != null) ? getFileNameWithPath() : "(null)")
				+ ", fileNameWithoutPath: " + ((getFileNameWithoutPath() != null) ? getFileNameWithoutPath() : "(null)")
				+ ", fileNameShort: " + ((getFileNameShort() != null) ? getFileNameShort() : "(null)")
				+ ", probeTime: " + getProbeTime()
				+ ", lastModified: " + getLastModified()
				+ ", streams.size: " + ((streams != null) ? streams.size() : "(null)")
				+ "}" ;
		return retMe ;
	}

}
