require 'asciidoctor'
require 'json'

if Gem::Version.new(Asciidoctor::VERSION) <= Gem::Version.new('2.0.0')
  fail 'asciidoctor: FAILED: HTML5/Slim backend needs Asciidoctor >=1.0.0!'
end

unless defined? Slim::Include
  fail 'asciidoctor: FAILED: HTML5/Slim backend needs Slim >= 4.0.0!'
end

# Add custom functions to this module that you want to use in your Slim
# templates. Within the template you can invoke them as top-level functions
# just like in Haml.
module Slim::Helpers
  QUOTE_TAGS = Asciidoctor::Converter::Html5Converter::QUOTE_TAGS
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
  # Conditionally wraps a block in an a element. If condition is +true+ then it
  # renders the a tag and the given block inside, otherwise it just renders the block.
  #
  # For example:
  #
  #    = html_a_tag_if link?
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
  #        render the enclosing a tag.
  # @yield (see #html_tag)
  # @return [String] a rendered HTML fragment.
  #
  def html_a_tag_if(condition, &block)
    if condition
      html_tag :a, {href: (attr :link)}, &block
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
                'xml','java','jfx','js','matlab','php','perl',
                'text','powershell','py','ruby','sql','sass',
                'scala','vb','yml']
    supported.include? lang
  end

  def map_to_confluence_supported_lang(lang)
      # Mapping of Asciidoctor/highlight.js language names to Confluence language names
      mapping = {
        'actionscript' => 'actionscript3', 'as' => 'actionscript3',
        'osascript' => 'applescript',
        'sh' => 'bash', 'zsh' => 'bash',
        'csharp' => 'c#', 'cs' => 'c#',
        'hpp' => 'cpp', 'cc' => 'cpp', 'hh' => 'cpp', 'c++' => 'cpp', 'h++' => 'cpp', 'cxx' => 'cpp',  'hxx' => 'cpp',
        'dpr' => 'delphi', 'dfm' => 'delphi', 'pas' => 'delphi', 'pascal' => 'delphi',
        'patch' => 'diff',
        'erlang' => 'erl',
        'html' => 'xml', 'xhtml' => 'xml', 'rss' => 'xml', 'atom' => 'xml', 'xjb' => 'xml', 'xsd' => 'xml', 'xsl' => 'xml', 'plist' => 'xml', 'svg' => 'xml',
        'jsp' => 'java',
        'javascript' => 'js', 'jsx' => 'js', 'json' => 'js',
        'pl' => 'perl', 'pm' => 'perl',
        'plaintext' => 'text', 'txt' => 'text',
        'ps' => 'powershell', 'ps1' => 'powershell',
        'python' => 'py', 'gyp' => 'py',
        'rb' => 'ruby', 'gemspec' => 'ruby', 'podspec' => 'ruby', 'thor' => 'ruby', 'irb' => 'ruby',
        'vbnet' => 'vb', 'vbscript' => 'vb', 'vbs' => 'vb',
        'yaml' => 'yml'
      }
      mapping[lang] || lang
    end

  #--------------------------------------------------------
  # inline_anchor
  #
  # @return [String, nil] text of the xref anchor, or +nil+ if not found.
  def xref_text
    if attributes[:refid] == text
      ref = document.catalog[:refs][attributes['refid'] || target]
      str = (ref ? ref.xreftext : text)
    else
      str = text
    end
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
    str.include? ".html"
  end

  # checks if xref is referring to an anchor on another adoc
  def cross_page_anchor_xref? str
    (str.include? ".html") && (str.include? "#")
  end

  # removes leading hash from anchor targets
  def anchor_name str
    if str.include? "#"
      str[(str.index('#')+1)..str.length]
    else
      str
    end
  end

  def confluence_inline_quoted node
      open, close, tag = QUOTE_TAGS[node.type]
      if node.id
        class_attr = node.role ? %( class="#{node.role}") : ''
        if tag
          %(#{open.chop} id="#{node.id}"#{class_attr}>#{node.text}#{close})
        else
          %(<span id="#{node.id}"#{class_attr}>#{open}#{node.text}#{close}</span>)
        end
      elsif node.role
        if tag
          %(#{open.chop} class="#{node.role}">#{node.text}#{close})
        elsif node.role == 'strike-through'
          %(<s>#{node.text}</s>)
        elsif node.role == 'line-through'
          %(<del>#{node.text}</del>)
        elsif node.role == 'underline'
          %(<u>#{node.text}</u>)
        else
          %(<span class="#{node.role}">#{open}#{node.text}#{close}</span>)
        end
      else
        %(#{open}#{node.text}#{close})
      end
    end

end
