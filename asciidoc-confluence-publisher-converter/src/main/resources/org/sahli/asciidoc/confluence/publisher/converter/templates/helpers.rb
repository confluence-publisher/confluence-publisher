require 'asciidoctor'
require 'json'

if Gem::Version.new(Asciidoctor::VERSION) <= Gem::Version.new('1.5.1')
  fail 'asciidoctor: FAILED: HTML5/Slim backend needs Asciidoctor >=1.5.2!'
end

unless defined? Slim::Include
  fail 'asciidoctor: FAILED: HTML5/Slim backend needs Slim >= 2.1.0!'
end

# Add custom functions to this module that you want to use in your Slim
# templates. Within the template you can invoke them as top-level functions
# just like in Haml.
module Slim::Helpers

  # Defaults
  DEFAULT_SECTNUMLEVELS = 3

  VOID_ELEMENTS = %w(area base br col command embed hr img input keygen link meta param source track wbr)

  CG_ALPHA = '[a-zA-Z]'
  CC_ALNUM = 'a-zA-Z0-9'

  # Detects strings that resemble URIs.
  #
  # Examples
  #   http://domain
  #   https://domain
  #   file:///path
  #   data:info
  #
  #   not c:/sample.adoc or c:\sample.adoc
  #
  UriSniffRx = %r{^#{CG_ALPHA}[#{CC_ALNUM}.+-]+:/{0,2}}

  ##
  # Creates an HTML tag with the given name and optionally attributes. Can take
  # a block that will run between the opening and closing tags.
  #
  # @param name [#to_s] the name of the tag.
  # @param attributes [Hash]
  # @param content [#to_s] the content; +nil+ to call the block.
  # @yield The block of Slim/HTML code within the tag (optional).
  # @return [String] a rendered HTML element.
  #
  def html_tag(name, attributes = {}, content = nil)
    attrs = attributes.reject { |_, v|
      v.nil? || (v.respond_to?(:empty?) && v.empty?)
    }.map do |k, v|
      v = v.compact.join(' ') if v.is_a? Array
      v = nil if v == true
      v = %("#{v}") if v
      [k, v] * '='
    end
    attrs_str = attrs.empty? ? '' : attrs.join(' ').prepend(' ')

    if VOID_ELEMENTS.include? name.to_s
      %(<#{name}#{attrs_str}>)
    else
      content ||= yield if block_given?
      %(<#{name}#{attrs_str}>#{content}</#{name}>)
    end
  end

  ##
  # Conditionally wraps a block in an element. If condition is +true+ then it
  # renders the specified tag with optional attributes and the given
  # block inside, otherwise it just renders the block.
  #
  # For example:
  #
  #    = html_tag_if link?, 'a', {class: 'image', href: (attr :link)}
  #      img src='./img/tux.png'
  #
  # will produce:
  #
  #    <a href="http://example.org" class="image">
  #      <img src="./img/tux.png">
  #    </a>
  #
  # if +link?+ is truthy, and just
  #
  #   <img src="./img/tux.png">
  #
  # otherwise.
  #
  # @param condition [Boolean] the condition to test to determine whether to
  #        render the enclosing tag.
  # @param name (see #html_tag)
  # @param attributes (see #html_tag)
  # @yield (see #html_tag)
  # @return [String] a rendered HTML fragment.
  #
  def html_tag_if(condition, name, attributes = {}, &block)
    if condition
      html_tag name, attributes, &block
    else
      yield
    end
  end

  ##
  # Returns corrected section level.
  #
  # @param sec [Asciidoctor::Section] the section node (default: self).
  # @return [Integer]
  #
  def section_level(sec = self)
    @_section_level ||= (sec.level == 0 && sec.special) ? 1 : sec.level
  end

  ##
  # Returns the captioned section's title, optionally numbered.
  #
  # @param sec [Asciidoctor::Section] the section node (default: self).
  # @return [String]
  #
  def section_title(sec = self)
    sectnumlevels = document.attr(:sectnumlevels, DEFAULT_SECTNUMLEVELS).to_i

    if sec.numbered && !sec.caption && sec.level <= sectnumlevels
      [sec.sectnum, sec.captioned_title].join(' ')
    else
      sec.captioned_title
    end
  end

  #--------------------------------------------------------
  # block_listing
  #

  def source_lang
    attr :language, nil, false
  end

  def confluence_supported_lang(lang)
    supported = ['actionscript3','applescript','bash','c#','cpp','css',
                'coldfusion','delphi','diff','erl','groovy',
                'xml','java','jfx','js','php','perl',
                'text','powershell','py','ruby','sql','sass',
                'scala','vb','yml']
    supported.include? lang
  end

  #--------------------------------------------------------
  # inline_anchor
  #
  # @return [String, nil] text of the xref anchor, or +nil+ if not found.
  def xref_text
    str = text || document.references[:ids][attr :refid || target]
    str.tr_s("\n", ' ') if str
  end

  # Public: Efficiently checks whether the specified String resembles a URI
  #
  # Uses the Asciidoctor::UriSniffRx regex to check whether the String begins
  # with a URI prefix (e.g., http://). No validation of the URI is performed.
  #
  # str - the String to check
  #
  # @return true if the String is a URI, false if it is not
  def uriish? str
    (str.include? ':') && str =~ UriSniffRx
  end

  # checks if xref is referring to adoc (internal cross references not yet supported)
  def cross_page_xref? str
    str.end_with? ".html"
  end

  # removes leading hash from anchor targets
  def anchor_name str
    if str.start_with? "#"
      str[1..str.length]
    else
      str
    end
  end

end
