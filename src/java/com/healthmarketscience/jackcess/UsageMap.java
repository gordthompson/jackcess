/*
Copyright (c) 2005 Health Market Science, Inc.

This library is free software; you can redistribute it and/or
modify it under the terms of the GNU Lesser General Public
License as published by the Free Software Foundation; either
version 2.1 of the License, or (at your option) any later version.

This library is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
Lesser General Public License for more details.

You should have received a copy of the GNU Lesser General Public
License along with this library; if not, write to the Free Software
Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
USA

You can contact Health Market Science at info@healthmarketscience.com
or at the following address:

Health Market Science
2700 Horizon Drive
Suite 200
King of Prussia, PA 19406
*/

package com.healthmarketscience.jackcess;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.logging.Handler;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Describes which database pages a particular table uses
 * @author Tim McCune
 */
public class UsageMap
{
  
  private static final Log LOG = LogFactory.getLog(UsageMap.class);
  
  /** Inline map type */
  public static final byte MAP_TYPE_INLINE = 0x0;
  /** Reference map type, for maps that are too large to fit inline */
  public static final byte MAP_TYPE_REFERENCE = 0x1;
  
  /** Page number of the map table declaration */
  private int _tablePageNum;
  /** Offset of the data page at which the usage map data starts */
  private int _startOffset;
  /** Offset of the data page at which the usage map declaration starts */
  private short _rowStart;
  /** Format of the database that contains this usage map */
  private JetFormat _format;
  /** First page that this usage map applies to */
  private int _startPage;
  /** Last page that this usage map applies to */
  private int _endPage;
  /** bits representing page numbers used, offset from _startPage */
  private BitSet _pageNumbers = new BitSet();
  /** Buffer that contains the usage map table declaration page */
  private ByteBuffer _tableBuffer;
  /** Used to read in pages */
  private PageChannel _pageChannel;
  /** modification count on the usage map, used to keep the iterators in
      sync */
  private int _modCount = 0;
  /** the current handler implementation for reading/writing the specific
      usage map type.  note, this may change over time. */
  private Handler _handler;
  
  /**
   * @param pageChannel Used to read in pages
   * @param tableBuffer Buffer that contains this map's declaration
   * @param pageNum Page number that this usage map is contained in
   * @param format Format of the database that contains this usage map
   * @param rowStart Offset at which the declaration starts in the buffer
   */
  private UsageMap(PageChannel pageChannel, ByteBuffer tableBuffer,
                   int pageNum, JetFormat format, short rowStart)
  throws IOException
  {
    _pageChannel = pageChannel;
    _tableBuffer = tableBuffer;
    _tablePageNum = pageNum;
    _format = format;
    _rowStart = rowStart;
    _tableBuffer.position((int) _rowStart + format.OFFSET_USAGE_MAP_START);
    _startOffset = _tableBuffer.position();
    if (LOG.isDebugEnabled()) {
      LOG.debug("Usage map block:\n" + ByteUtil.toHexString(_tableBuffer, _rowStart,
          tableBuffer.limit() - _rowStart));
    }
  }

  /**
   * @param pageChannel Used to read in pages
   * @param pageNum Page number that this usage map is contained in
   * @param rowNum Number of the row on the page that contains this usage map
   * @param format Format of the database that contains this usage map
   * @return Either an InlineUsageMap or a ReferenceUsageMap, depending on
   *         which type of map is found
   */
  public static UsageMap read(PageChannel pageChannel, int pageNum,
                              byte rowNum, JetFormat format,
                              boolean assumeOutOfRangeBitsOn)
    throws IOException
  {
    ByteBuffer tableBuffer = pageChannel.createPageBuffer();
    pageChannel.readPage(tableBuffer, pageNum);
    short rowStart = Table.findRowStart(tableBuffer, rowNum, format);
    int rowEnd = Table.findRowEnd(tableBuffer, rowNum, format);
    tableBuffer.limit(rowEnd);    
    byte mapType = tableBuffer.get(rowStart);
    UsageMap rtn = new UsageMap(pageChannel, tableBuffer, pageNum, format,
                                rowStart);
    rtn.initHandler(mapType, assumeOutOfRangeBitsOn);
    return rtn;
  }

  private void initHandler(byte mapType, boolean assumeOutOfRangeBitsOn)
    throws IOException
  {
    if (mapType == MAP_TYPE_INLINE) {
      _handler = new InlineHandler(assumeOutOfRangeBitsOn);
    } else if (mapType == MAP_TYPE_REFERENCE) {
      _handler = new ReferenceHandler();
    } else {
      throw new IOException("Unrecognized map type: " + mapType);
    }
  }
  
  public PageIterator iterator() {
    return new ForwardPageIterator();
  }

  public PageIterator reverseIterator() {
    return new ReversePageIterator();
  }
  
  protected short getRowStart() {
    return _rowStart;
  }
  
  protected void setStartOffset(int startOffset) {
    _startOffset = startOffset;
  }
  
  protected int getStartOffset() {
    return _startOffset;
  }
  
  protected ByteBuffer getTableBuffer() {
    return _tableBuffer;
  }
  
  protected int getTablePageNumber() {
    return _tablePageNum;
  }
  
  protected PageChannel getPageChannel() {
    return _pageChannel;
  }
  
  protected JetFormat getFormat() {
    return _format;
  }

  protected int getStartPage() {
    return _startPage;
  }
    
  protected int getEndPage() {
    return _endPage;
  }
    
  protected BitSet getPageNumbers() {
    return _pageNumbers;
  }

  protected void setPageRange(int newStartPage, int newEndPage) {
    _startPage = newStartPage;
    _endPage = newEndPage;
  }

  protected boolean isPageWithinRange(int pageNumber)
  {
    return((pageNumber >= _startPage) && (pageNumber < _endPage));
  }

  protected int getFirstPageNumber() {
    return bitIndexToPageNumber(getNextBitIndex(-1));
  }

  protected int getNextPageNumber(int curPage) {
    return bitIndexToPageNumber(
        getNextBitIndex(pageNumberToBitIndex(curPage)));
  }    
  
  protected int getNextBitIndex(int curIndex) {
    return _pageNumbers.nextSetBit(curIndex + 1);
  }    
  
  protected int getLastPageNumber() {
    return bitIndexToPageNumber(getPrevBitIndex(_pageNumbers.length()));
  }

  protected int getPrevPageNumber(int curPage) {
    return bitIndexToPageNumber(
        getPrevBitIndex(pageNumberToBitIndex(curPage)));
  }    
  
  protected int getPrevBitIndex(int curIndex) {
    --curIndex;
    while((curIndex >= 0) && !_pageNumbers.get(curIndex)) {
      --curIndex;
    }
    return curIndex;
  }    
  
  protected int bitIndexToPageNumber(int bitIndex) {
    return((bitIndex >= 0) ? (_startPage + bitIndex) :
           PageChannel.INVALID_PAGE_NUMBER);
  }

  protected int pageNumberToBitIndex(int pageNumber) {
    return((pageNumber != PageChannel.INVALID_PAGE_NUMBER) ?
           (pageNumber - _startPage) : -1);
  }

  protected void clearTableAndPages()
  {
    // reset some values
    _pageNumbers.clear();
    _startPage = 0;
    _endPage = 0;
    ++_modCount;
    
    // clear out the table data
    int tableStart = getRowStart() + getFormat().OFFSET_USAGE_MAP_START - 4;
    int tableEnd = tableStart + getFormat().USAGE_MAP_TABLE_BYTE_LENGTH + 4;
    ByteUtil.clearRange(_tableBuffer, tableStart, tableEnd);
  }
  
  protected void writeTable()
    throws IOException
  {
    // note, we only want to write the row data with which we are working
    _pageChannel.writePage(_tableBuffer, _tablePageNum, _rowStart);
  }
  
  /**
   * Read in the page numbers in this inline map
   */
  protected void processMap(ByteBuffer buffer, int bufferStartPage)
  {
    int byteCount = 0;
    while (buffer.hasRemaining()) {
      byte b = buffer.get();
      if(b != (byte)0) {
        for (int i = 0; i < 8; i++) {
          if ((b & (1 << i)) != 0) {
            int pageNumberOffset = (byteCount * 8 + i) + bufferStartPage;
            _pageNumbers.set(pageNumberOffset);
          }
        }
      }
      byteCount++;
    }
  }
  
  /**
   * Add a page number to this usage map
   */
  public void addPageNumber(int pageNumber) throws IOException {
    ++_modCount;
    _handler.addOrRemovePageNumber(pageNumber, true);
  }
  
  /**
   * Remove a page number from this usage map
   */
  public void removePageNumber(int pageNumber) throws IOException {
    ++_modCount;
    _handler.addOrRemovePageNumber(pageNumber, false);
  }
  
  protected void updateMap(int absolutePageNumber,
                           int bufferRelativePageNumber,
                           ByteBuffer buffer, boolean add)
    throws IOException
  {
    //Find the byte to which to apply the bitmask and create the bitmask
    int offset = bufferRelativePageNumber / 8;
    int bitmask = 1 << (bufferRelativePageNumber % 8);
    byte b = buffer.get(_startOffset + offset);

    // check current value for this page number
    int pageNumberOffset = pageNumberToBitIndex(absolutePageNumber);
    boolean isOn = _pageNumbers.get(pageNumberOffset);
    if(isOn == add) {
      throw new IOException("Page number " + absolutePageNumber + " already " +
                            ((add) ? "added to" : "removed from") +
                            " usage map");
    }
    
    //Apply the bitmask
    if (add) {
      b |= bitmask;
      _pageNumbers.set(pageNumberOffset);
    } else {
      b &= ~bitmask;
      _pageNumbers.clear(pageNumberOffset);
    }
    buffer.put(_startOffset + offset, b);
  }

  /**
   * Promotes and inline usage map to a reference usage map.
   */
  private void promoteInlineHandlerToReferenceHandler(int newPageNumber)
    throws IOException
  {
    // copy current page number info to new references and then clear old
    int oldStartPage = _startPage;
    BitSet oldPageNumbers = (BitSet)_pageNumbers.clone();

    // clear out the main table (inline usage map data and start page)
    clearTableAndPages();
    
    // set the new map type
    _tableBuffer.put(getRowStart(), MAP_TYPE_REFERENCE);

    // write the new table data
    writeTable();
    
    // set new handler
    _handler = new ReferenceHandler();

    // update new handler with old data
    reAddPages(oldStartPage, oldPageNumbers, newPageNumber);
  }

  private void reAddPages(int oldStartPage, BitSet oldPageNumbers,
                          int newPageNumber)
    throws IOException
  {
    // add all the old pages back in
    for(int i = oldPageNumbers.nextSetBit(0); i >= 0;
        i = oldPageNumbers.nextSetBit(i + 1)) {
      addPageNumber(oldStartPage + i);
    }

    if(newPageNumber != PageChannel.INVALID_PAGE_NUMBER) {
      // and then add the new page
      addPageNumber(newPageNumber);
    }
  }
  
  public String toString() {
    StringBuilder builder = new StringBuilder("page numbers: [");
    for(PageIterator iter = iterator(); iter.hasNextPage(); ) {
      builder.append(iter.getNextPage());
      if(iter.hasNextPage()) {
        builder.append(", ");
      }
    }
    builder.append("]");
    return builder.toString();
  }
  
  private abstract class Handler
  {
    protected Handler() {
    }
    
    /**
     * @param pageNumber Page number to add or remove from this map
     * @param add True to add it, false to remove it
     */
    public abstract void addOrRemovePageNumber(int pageNumber, boolean add)
      throws IOException;
  }

  /**
   * Usage map whose map is written inline in the same page.  This type of map
   * can contain a maximum of 512 pages, and is always used for free space
   * maps.  It has a start page, which all page numbers in its map are
   * calculated as starting from.
   * @author Tim McCune
   */
  private class InlineHandler extends Handler
  {
    private final boolean _assumeOutOfRangeBitsOn;
    
    private InlineHandler(boolean assumeOutOfRangeBitsOn)
      throws IOException
    {
      _assumeOutOfRangeBitsOn = assumeOutOfRangeBitsOn;
      int startPage = getTableBuffer().getInt(getRowStart() + 1);
      setInlinePageRange(startPage);
      processMap(getTableBuffer(), 0);
    }

    private int getMaxInlinePages() {
      return(getFormat().USAGE_MAP_TABLE_BYTE_LENGTH * 8);
    }
    
    /**
     * Sets the page range for an inline usage map starting from the given
     * page.
     */
    private void setInlinePageRange(int startPage) {
      setPageRange(startPage, startPage + getMaxInlinePages());
    }      
    
    @Override
    public void addOrRemovePageNumber(int pageNumber, boolean add)
      throws IOException
    {
      if(isPageWithinRange(pageNumber)) {

        // easy enough, just update the inline data
        int bufferRelativePageNumber = pageNumberToBitIndex(pageNumber);
        updateMap(pageNumber, bufferRelativePageNumber, getTableBuffer(), add);
        // Write the updated map back to disk
        writeTable();
        
      } else {

        // uh-oh, we've split our britches.  what now?  determine what our
        // status is
        int firstPage = getFirstPageNumber();
        int lastPage = getLastPageNumber();
        
        if(add) {

          // we can ignore out-of-range page addition if we are already
          // assuming out-of-range bits are "on".  Note, we are leaving small
          // holes in the database here (leaving behind some free pages), but
          // it's not the end of the world.
          if(!_assumeOutOfRangeBitsOn) {
            
            // we are adding, can we shift the bits and stay inline?
            if(firstPage == PageChannel.INVALID_PAGE_NUMBER) {
              // no pages currently
              firstPage = pageNumber;
              lastPage = pageNumber;
            } else if(pageNumber > lastPage) {
              lastPage = pageNumber;
            } else {
              firstPage = pageNumber;
            }
            if((lastPage - firstPage + 1) < getMaxInlinePages()) {

              // we can still fit within an inline map
              moveToNewStartPage(firstPage, pageNumber);
            
            } else {
              // not going to happen, need to promote the usage map to a
              // reference map
              promoteInlineHandlerToReferenceHandler(pageNumber);
            }
          }
        } else {

          // we are removing, what does that mean?
          if(_assumeOutOfRangeBitsOn) {

            // we are using an inline map and assuming that anything not
            // within the current range is "on".  so, if we attempt to set a
            // bit which is before the current page, ignore it, we are not
            // going back for it.
            if((firstPage == PageChannel.INVALID_PAGE_NUMBER) ||
               (pageNumber > lastPage)) {

              // move to new start page, filling in as we move
              moveToNewStartPageForRemove(firstPage, lastPage, pageNumber);
              
            }
            
          } else {

            // this should not happen, we are removing a page which is not in
            // the map
            throw new IOException("Page number " + pageNumber +
                                  " already removed from usage map");
          }
        }

      }
    }

    /**
     * Shifts the inline usage map so that it now starts with the given page.
     * @param newStartPage new page at which to start
     * @param newPageNumber optional page number to add once the map has been
     *                      shifted to the new start page
     */
    private void moveToNewStartPage(int newStartPage, int newPageNumber)
      throws IOException
    {
      int oldStartPage = getStartPage();
      BitSet oldPageNumbers = (BitSet)getPageNumbers().clone();

      // clear out the main table (inline usage map data and start page)
      clearTableAndPages();

      // write new start page
      ByteBuffer tableBuffer = getTableBuffer();
      tableBuffer.position(getRowStart() + 1);
      tableBuffer.putInt(newStartPage);

      // write the new table data
      writeTable();

      // set new page range
      setInlinePageRange(newStartPage);

      // put the pages back in
      reAddPages(oldStartPage, oldPageNumbers, newPageNumber);
    }

    /**
     * Shifts the inline usage map so that it now starts with the given
     * firstPage (if valid), otherwise the newPageNumber.  Any page numbers
     * added to the end of the usage map are set to "on".
     * @param firstPage current first used page
     * @param lastPage current last used page
     * @param newPageNumber page number to remove once the map has been
     *                      shifted to the new start page
     */
    private void moveToNewStartPageForRemove(int firstPage,
                                             int lastPage,
                                             int newPageNumber)
      throws IOException
    {
      int newStartPage = firstPage;
      if(newStartPage == PageChannel.INVALID_PAGE_NUMBER) {
        newStartPage = newPageNumber;
      } else if((newPageNumber - newStartPage + 1) >=
                getMaxInlinePages()) {
        // this will not move us far enough to hold the new page.  just
        // discard any initial unused pages
        newStartPage += (newPageNumber - getMaxInlinePages() + 1);
      }
      
      // move the current data
      moveToNewStartPage(newStartPage, PageChannel.INVALID_PAGE_NUMBER);

      if(firstPage == PageChannel.INVALID_PAGE_NUMBER) {
        
        // this is the common case where we left everything behind
        int tableStart = getRowStart() + getFormat().OFFSET_USAGE_MAP_START;
        int tableEnd = tableStart + getFormat().USAGE_MAP_TABLE_BYTE_LENGTH;
        ByteUtil.fillRange(_tableBuffer, tableStart, tableEnd);

        // write out the updated table
        writeTable();

        // "add" all the page numbers
        getPageNumbers().set(0, getMaxInlinePages());

      } else {

        // add every new page manually
        for(int i = (lastPage + 1); i < getEndPage(); ++i) {
          addPageNumber(i);
        }
      }

      // lastly, remove the new page
      removePageNumber(newPageNumber);
    }
  }

  /**
   * Usage map whose map is written across one or more entire separate pages
   * of page type USAGE_MAP.  This type of map can contain 32736 pages per
   * reference page, and a maximum of 16 reference map pages for a total
   * maximum of 523776 pages (2 GB).
   * @author Tim McCune
   */
  private class ReferenceHandler extends Handler
  {
    /** Buffer that contains the current reference map page */ 
    private final TempPageHolder _mapPageHolder =
      TempPageHolder.newHolder(false);
  
    private ReferenceHandler()
      throws IOException
    {
      int numUsagePages = (getFormat().USAGE_MAP_TABLE_BYTE_LENGTH / 4) + 1;
      setStartOffset(getFormat().OFFSET_USAGE_MAP_PAGE_DATA);
      setPageRange(0, (numUsagePages * getMaxPagesPerUsagePage()));
      
      // there is no "start page" for a reference usage map, so we get an
      // extra page reference on top of the number of page references that fit
      // in the table
      for (int i = 0; i < numUsagePages; i++) {
        int mapPageNum = getTableBuffer().getInt(
            calculateMapPagePointerOffset(i));
        if (mapPageNum > 0) {
          ByteBuffer mapPageBuffer =
            _mapPageHolder.setPage(getPageChannel(), mapPageNum);
          byte pageType = mapPageBuffer.get();
          if (pageType != PageTypes.USAGE_MAP) {
            throw new IOException("Looking for usage map at page " +
                                  mapPageNum + ", but page type is " +
                                  pageType);
          }
          mapPageBuffer.position(getFormat().OFFSET_USAGE_MAP_PAGE_DATA);
          processMap(mapPageBuffer, (getMaxPagesPerUsagePage() * i));
        }
      }
    }

    private int getMaxPagesPerUsagePage() {
      return((getFormat().PAGE_SIZE - getFormat().OFFSET_USAGE_MAP_PAGE_DATA)
             * 8);
    }
        
    @Override
    public void addOrRemovePageNumber(int pageNumber, boolean add)
      throws IOException
    {
      if(!isPageWithinRange(pageNumber)) {
        throw new IOException("Page number " + pageNumber +
                              " is out of supported range");
      }
      int pageIndex = (int)(pageNumber / getMaxPagesPerUsagePage());
      int mapPageNum = getTableBuffer().getInt(
          calculateMapPagePointerOffset(pageIndex));
      if(mapPageNum <= 0) {
        // Need to create a new usage map page
        mapPageNum  = createNewUsageMapPage(pageIndex);
      }
      ByteBuffer mapPageBuffer = _mapPageHolder.setPage(getPageChannel(),
                                                        mapPageNum);
      updateMap(pageNumber,
                (pageNumber - (getMaxPagesPerUsagePage() * pageIndex)),
                mapPageBuffer, add);
      getPageChannel().writePage(mapPageBuffer, mapPageNum);
    }
  
    /**
     * Create a new usage map page and update the map declaration with a
     * pointer to it.
     * @param pageIndex Index of the page reference within the map declaration 
     */
    private int createNewUsageMapPage(int pageIndex) throws IOException {
      ByteBuffer mapPageBuffer = _mapPageHolder.setNewPage(getPageChannel());
      mapPageBuffer.put(PageTypes.USAGE_MAP);
      mapPageBuffer.put((byte) 0x01);  //Unknown
      mapPageBuffer.putShort((short) 0); //Unknown
      for(int i = 0; i < mapPageBuffer.limit(); ++i) {
        mapPageBuffer.get(i);
      }
      int mapPageNum = _mapPageHolder.getPageNumber();
      getTableBuffer().putInt(calculateMapPagePointerOffset(pageIndex),
                             mapPageNum);
      writeTable();
      return mapPageNum;
    }
  
    private int calculateMapPagePointerOffset(int pageIndex) {
      return getRowStart() + getFormat().OFFSET_REFERENCE_MAP_PAGE_NUMBERS +
        (pageIndex * 4);
    }
  }
  
  
  /**
   * Utility class to iterate over the pages in the UsageMap.  Note, since the
   * iterators hold on to page numbers, they should stay valid even as the
   * usage map handlers shift around the bits.
   */
  public abstract class PageIterator
  {
    /** the next used page number */
    private int _nextPageNumber;
    /** the previous used page number */
    private int _prevPageNumber;
    /** the last read modification count on the UsageMap.  we track this so
        that the iterator can detect updates to the usage map while iterating
        and act accordingly */
    private int _lastModCount;

    protected PageIterator() {
    }

    /**
     * @return {@code true} if there is another valid page, {@code false}
     *         otherwise.
     */
    public final boolean hasNextPage() {
      if((_nextPageNumber == PageChannel.INVALID_PAGE_NUMBER) &&
         (_lastModCount != _modCount)) {
        // recheck the last page, in case more showed up
        if(_prevPageNumber == PageChannel.INVALID_PAGE_NUMBER) {
          // we were at the beginning
          reset();
        } else {
          _lastModCount = _modCount;
          _nextPageNumber = getNextPage(_prevPageNumber);
        }
      }
      return(_nextPageNumber != PageChannel.INVALID_PAGE_NUMBER);
    }      
    
    /**
     * @return valid page number if there was another page to read,
     *         {@link PageChannel#INVALID_PAGE_NUMBER} otherwise
     */
    public final int getNextPage() {
      if (hasNextPage()) {
        _lastModCount = _modCount;
        _prevPageNumber = _nextPageNumber;
        _nextPageNumber = getNextPage(_nextPageNumber);
        return _prevPageNumber;
      }
      return PageChannel.INVALID_PAGE_NUMBER;
    }

    /**
     * After calling this method, getNextPage will return the first page in
     * the map
     */
    public final void reset() {
      _lastModCount = _modCount;
      _prevPageNumber = PageChannel.INVALID_PAGE_NUMBER;
      _nextPageNumber = getInitialPage();
    }

    protected abstract int getInitialPage();

    protected abstract int getNextPage(int curPage);
  }
  
  /**
   * Utility class to iterate forward over the pages in the UsageMap.
   */
  public class ForwardPageIterator extends PageIterator
  {
    private ForwardPageIterator() {
      reset();
    }
    
    @Override
    protected int getNextPage(int curPage) {
      return UsageMap.this.getNextPageNumber(curPage);
    }

    @Override
    protected int getInitialPage() {
      return UsageMap.this.getFirstPageNumber();
    }
  }
  
  /**
   * Utility class to iterate backward over the pages in the UsageMap.
   */
  public class ReversePageIterator extends PageIterator
  {
    private ReversePageIterator() {
      reset();
    }
    
    @Override
    protected int getNextPage(int curPage) {
      return UsageMap.this.getPrevPageNumber(curPage);
    }

    @Override
    protected int getInitialPage() {
      return UsageMap.this.getLastPageNumber();
    }
  }

  
}
