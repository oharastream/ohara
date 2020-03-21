..
.. Copyright 2019 is-land
..
.. Licensed under the Apache License, Version 2.0 (the "License");
.. you may not use this file except in compliance with the License.
.. You may obtain a copy of the License at
..
..     http://www.apache.org/licenses/LICENSE-2.0
..
.. Unless required by applicable law or agreed to in writing, software
.. distributed under the License is distributed on an "AS IS" BASIS,
.. WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
.. See the License for the specific language governing permissions and
.. limitations under the License.
..

.. _quick-rst:

reStructuredText quick example
==============================

.. note::
  This page is **not** a reStructuredText Markup tutorial. If you are looking for tutorial or more detail about
  reStructuredText, here are some reference links:

  * `A ReStructuredText Primer <http://docutils.sourceforge.net/docs/user/rst/quickstart.html>`_
  * `Quick reStructuredText <http://docutils.sourceforge.net/docs/user/rst/quickref.html>`_
  * `Markup Specification <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#sections>`_
  * https://thomas-cokelaer.info/tutorials/sphinx/rest_syntax.html
  * [中文] `reStructuredText 簡介 <https://zh-sphinx-doc.readthedocs.io/en/latest/rest.html>`_
  * [中文] `reStructuredText 学习笔记 <http://notes.tanchuanqi.com/tools/reStructuredText.html>`_

Basic
---------

Section Structure
^^^^^^^^^^^^^^^^^

An underline/overline is a single repeated punctuation character that begins in column 1 and forms a line extending at
least as far as the right edge of the title text.

Example
  .. code-block:: text

   Level 1
   ===========

   Level 2
   -----------

   Level 3
   ^^^^^^^^^^^

   Level 4
   """""""""""

Description
  - ``=`` --> Title level 1, Page title
  - ``-`` --> Title level 2, Section heading
  - ``^`` --> Title level 3, Sub section heading
  - ``"`` --> Title level 4, Sub sub-section heading

  .. note::
    Please use the list above as section underline character in Ohara.


Inline Markup
^^^^^^^^^^^^^^^

Example
  .. code-block:: text

    * *emphasis*
    * **strong emphasis**
    * ``inline literal``
    * Partial character emphasis: Collection\ **s**
    * Substitution: |ohara-intro|

      * please see `Substitution section <#quick-example-substitution>`_

  * *emphasis*
  * **strong emphasis**
  * ``inline literal``
  * Partial character emphasis: Collection\ **s**
  * Substitution: |ohara-intro|

    * please see `Substitution section <#quick-example-substitution>`_


Tables
------

ReStructuredText provides two syntaxes for delineating table cells: **Grid Tables** and **Simple Tables**.

Simple Tables
^^^^^^^^^^^^^

Example 1
  .. code-block:: text

    =====  =====  =======
      A      B    A and B
    =====  =====  =======
    False  False  False
    True   False  False
    True   True   True
    =====  =====  =======

  =====  =====  =======
    A      B    A and B
  =====  =====  =======
  False  False  False
  True   False  False
  True   True   True
  =====  =====  =======

Example 2
  .. code-block:: text

    =====  =====  ======
       Inputs     Output
    ------------  ------
      A      B    A or B
    =====  =====  ======
    False  False  False
    True   False  True
    True   True   True
    =====  =====  ======

  =====  =====  ======
     Inputs     Output
  ------------  ------
    A      B    A or B
  =====  =====  ======
  False  False  False
  True   False  True
  True   True   True
  =====  =====  ======

Example 3
  .. code-block:: text

    =====  =====
    col 1  col 2
    =====  =====
    1      Second column of row 1.
    2      Second column of row 2.
           Second line of paragraph.
    3      - Second column of row 3.

           - Second item in bullet
             list (row 3, column 2).
    \      Row 4; column 1 will be empty.
    =====  =====

  =====  =====
  col 1  col 2
  =====  =====
  1      Second column of row 1.
  2      Second column of row 2.
         Second line of paragraph.
  3      - Second column of row 3.

         - Second item in bullet
           list (row 3, column 2).
  \      Row 4; column 1 will be empty.
  =====  =====

Reference for detail:
  - `Simple Tables <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#simple-tables>`_


Grid Tables
^^^^^^^^^^^

Example
  .. code-block:: text

    +------------------------+------------+----------+----------+
    | Header row, column 1   | Header 2   | Header 3 | Header 4 |
    | (header rows optional) |            |          |          |
    +========================+============+==========+==========+
    | body row 1, column 1   | column 2   | column 3 | column 4 |
    +------------------------+------------+----------+----------+
    | body row 2             | Cells may span columns.          |
    +------------------------+------------+---------------------+
    | body row 3             | Cells may  | - Table cells       |
    +------------------------+ span rows. | - contain           |
    | body row 4             |            | - body elements.    |
    +------------------------+------------+---------------------+

  +------------------------+------------+----------+----------+
  | Header row, column 1   | Header 2   | Header 3 | Header 4 |
  | (header rows optional) |            |          |          |
  +========================+============+==========+==========+
  | body row 1, column 1   | column 2   | column 3 | column 4 |
  +------------------------+------------+----------+----------+
  | body row 2             | Cells may span columns.          |
  +------------------------+------------+---------------------+
  | body row 3             | Cells may  | - Table cells       |
  +------------------------+ span rows. | - contain           |
  | body row 4             |            | - body elements.    |
  +------------------------+------------+---------------------+

Reference for detail:
  - `Grid Tables <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#grid-tables>`_


List
----


Bullet Lists
^^^^^^^^^^^^

Example
  .. code-block:: text

    - This is the first bullet list item.  The blank line above the
      first list item is required; blank lines between list items
      (such as below this paragraph) are optional.

    - This is the first paragraph in the second item in the list.

      This is the second paragraph in the second item in the list.
      The blank line above this paragraph is required.  The left edge
      of this paragraph lines up with the paragraph above, both
      indented relative to the bullet.

      - This is a sublist.  The bullet lines up with the left edge of
        the text blocks above.  A sublist is a new list so requires a
        blank line above and below.

    - This is the third item of the main list.

    This paragraph is not part of the list.

  - This is the first bullet list item.  The blank line above the
    first list item is required; blank lines between list items
    (such as below this paragraph) are optional.

  - This is the first paragraph in the second item in the list.

    This is the second paragraph in the second item in the list.
    The blank line above this paragraph is required.  The left edge
    of this paragraph lines up with the paragraph above, both
    indented relative to the bullet.

    - This is a sublist.  The bullet lines up with the left edge of
      the text blocks above.  A sublist is a new list so requires a
      blank line above and below.

  - This is the third item of the main list.

  This paragraph is not part of the list.

Reference for detail:
  - `Bullet Lists <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#bullet-lists>`_


Enumerated Lists
^^^^^^^^^^^^^^^^

Example
  .. code-block:: text

    #. Item 1 initial text.

       #. Item 1.1.
       #. Item 1.1.

    #. a) Item 2.a.
       b) Item 2.b.

  #. Item 1 initial text.

     #. Item 1.1.
     #. Item 1.2.

  #. a) Item 2.a.
     b) Item 2.b.

Reference for detail:
  - `Enumerated Lists <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#enumerated-lists>`_


Definition Lists
^^^^^^^^^^^^^^^^

Example
  .. code-block:: text

    term 1
        Definition 1.

    term 2
        Definition 2, paragraph 1.

        Definition 2, paragraph 2.

    term 3 : classifier
        Definition 3.

    term 4 : classifier one : classifier two
        Definition 4.

  term 1
      Definition 1.

  term 2
      Definition 2, paragraph 1.

      Definition 2, paragraph 2.

  term 3 : classifier
      Definition 3.

  term 4 : classifier one : classifier two
      Definition 4.

Reference for detail:
  - `Definition Lists <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#definition-lists>`_


Field Lists
^^^^^^^^^^^

Example
  .. code-block:: text

    :Date: 2001-08-16
    :Version: 1
    :Authors: - Me
              - Myself
              - I
    :Indentation: Since the field marker may be quite long, the second
       and subsequent lines of the field body do not have to line up
       with the first line, but they must be indented relative to the
       field name marker, and they must line up with each other.
    :Parameter i: integer

  :Date: 2001-08-16
  :Version: 1
  :Authors: - Me
            - Myself
            - I
  :Indentation: Since the field marker may be quite long, the second
     and subsequent lines of the field body do not have to line up
     with the first line, but they must be indented relative to the
     field name marker, and they must line up with each other.
  :Parameter i: integer

Reference for detail:
  - `Field Lists <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#option-lists>`_


Option Lists
^^^^^^^^^^^^

Example
  .. code-block:: text

    -a         Output all.
    -b         Output both (this description is
               quite long).
    -c arg     Output just arg.
    --long     Output all day long.

    -p         This option has two paragraphs in the description.
               This is the first.

               This is the second.  Blank lines may be omitted between
               options (as above) or left in (as here and below).

    --very-long-option  A VMS-style option.  Note the adjustment for
                        the required two spaces.

    --an-even-longer-option
               The description can also start on the next line.

    -2, --two  This option has two variants.

    -f FILE, --file=FILE  These two options are synonyms; both have
                          arguments.

    /V         A VMS/DOS-style option.

  -a         Output all.
  -b         Output both (this description is
             quite long).
  -c arg     Output just arg.
  --long     Output all day long.

  -p         This option has two paragraphs in the description.
             This is the first.

             This is the second.  Blank lines may be omitted between
             options (as above) or left in (as here and below).

  --very-long-option  A VMS-style option.  Note the adjustment for
                      the required two spaces.

  --an-even-longer-option
             The description can also start on the next line.

  -2, --two  This option has two variants.

  -f FILE, --file=FILE  These two options are synonyms; both have
                        arguments.

  /V         A VMS/DOS-style option.

Reference for detail:
  - `Option Lists <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#option-lists>`_


..
.. TODO: more Blocks example
..

Hyperlinks
----------

External hyperlink targets
^^^^^^^^^^^^^^^^^^^^^^^^^^

Example
  .. code-block:: text

    - External hyperlink: https://ohara.readthedocs.io/
    - External hyperlink: `<https://ohara.readthedocs.io/>`_
    - External hyperlink: `Please see Ohara document <https://ohara.readthedocs.io/>`_
    - External hyperlink: Ohara_
    - External hyperlink: `Ohara Document`_

    .. _Ohara: https://github.com/oharastream/ohara
    .. _Ohara Document: https://ohara.readthedocs.io/en/latest/

  - External hyperlink: https://ohara.readthedocs.io/
  - External hyperlink: `<https://ohara.readthedocs.io/>`_
  - External hyperlink: `Please see Ohara document <https://ohara.readthedocs.io/>`_
  - External hyperlink: Ohara_
  - External hyperlink: `Ohara Document`_

  .. _Ohara: https://github.com/oharastream/ohara
  .. _Ohara Document: https://ohara.readthedocs.io/en/latest/

.. _quick-example-internal-targets:

Internal hyperlink targets
^^^^^^^^^^^^^^^^^^^^^^^^^^

Internal hyperlink targets is standard reST label, must be **unique** throughout the entire documentation.

Example
  .. code-block:: text

    .. _quick-rst-internal-targets:

    Internal hyperlink targets
    ^^^^^^^^^^^^^^^^^^^^^^^^^^

    - Goto :ref:`Internal hyperlink targets <quick-example-internal-targets>`
    - Goto quick-example-internal-targets_
    - Goto `Internal hyperlink targets <#quick-example-internal-targets>`_

  - Goto :ref:`Internal hyperlink targets <quick-example-internal-targets>`
  - Goto quick-example-internal-targets_
  - Goto `Internal hyperlink targets <#quick-example-internal-targets>`_

.. note::
  We can use `:ref: <https://www.sphinx-doc.org/en/master/usage/restructuredtext/roles.html#cross-referencing-arbitrary-locations>`_
  to cross-referencing arbitrary locations in any document.

.. warning::
  Please don't point to the **implicit hyperlink targets** that auto generated by section titles.
  Use **internal hyperlink targets** instead to keep the internal link not broken.

Reference for detail:
  - `Hyperlink Targets <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#hyperlink-targets>`_
  - `Hyperlink References <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#hyperlink-references>`_
  - `Cross-referencing anything <https://www.sphinx-doc.org/en/master/usage/restructuredtext/roles.html#cross-referencing-arbitrary-locations>`_


Predefined external links in Ohara
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

Ohara enable `sphinx.ext.extlinks`_ to help with common pattern to point to the external sites.
Following extlinks are define in **docs/src/conf.py**:

  .. code-block:: python

    extlinks = {
        'ohara-issue': ('https://github.com/oharastream/ohara/issues/%s', '#'),
        'ohara-source': ('https://github.com/oharastream/ohara/blob/%s/' % ohara_branch + "%s", ''),
        'kafka-issue': ('https://issues.apache.org/jira/browse/KAFKA-%s', 'KAFKA-'),
        'zookeeper-issue': ('https://issues.apache.org/jira/browse/ZOOKEEPER-%s', 'ZOOKEEPER-'),
        'k8s-issue': ('https://github.com/kubernetes/kubernetes/issues/%s', '#')
    }

Example
  .. code-block:: text

    - Ohara issue link: :ohara-issue:`800`
    - Ohara source link: :ohara-source:`PageViewRegionExample <ohara-stream/src/test/java/oharastream/ohara/stream/examples/PageViewRegionExample.java>`
    - Kafka issue: :kafka-issue:`8765`
    - Zookeeper issue: :zookeeper-issue:`2345`
    - Kubernetes issue: :k8s-issue:`2345`

  - Ohara issue link: :ohara-issue:`800`
  - Ohara source link: :ohara-source:`PageViewRegionExample <ohara-stream/src/test/java/oharastream/ohara/stream/examples/PageViewRegionExample.java>`
  - Kafka issue: :kafka-issue:`8765`
  - Zookeeper issue: :zookeeper-issue:`2345`
  - Kubernetes issue: :k8s-issue:`2345`


.. note::
  The extlink ``:ohara-source:`` also bind *branch* variable to make sure that we can point to
  the correct url.

.. _sphinx.ext.extlinks: https://www.sphinx-doc.org/en/master/usage/extensions/extlinks.html#module-sphinx.ext.extlinks

.. _quick-rst-substitution:

Substitution
------------

Substitution definition
^^^^^^^^^^^^^^^^^^^^^^^

Example
  .. code-block:: text

    - About Ohara: |ohara-intro|
    - The |happy-face| symbol must be used on containers used to dispose of medical waste.

    .. |ohara-intro| replace:: Ohara, a big data platform which is powered by Apache Kafka and Docker, enables effective and quick ways of building application at streaming and scale.
    .. |happy-face| image:: https://cdn4.iconfinder.com/data/icons/emoji-18/61/2-32.png

  - About Ohara: |ohara-intro|
  - The |happy-face| symbol must be used when you are **very**, **very** happy.

  .. |ohara-intro| replace:: Ohara, a big data platform which is powered by Apache Kafka and Docker, enables effective and quick ways of building application at streaming and scale.
  .. |happy-face| image:: https://cdn4.iconfinder.com/data/icons/emoji-18/61/2-32.png

.. tip::
  Your substitution definition only available in the same file.

Reference for detail:
  - `Substitution Definitions <http://docutils.sourceforge.net/docs/ref/rst/restructuredtext.html#substitution-definitions>`_
  - `Replacement Text <http://docutils.sourceforge.net/docs/ref/rst/directives.html#replacement-text>`_


Predefine substitution definition
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^

There are two important substitution definitions: **version**, **branch**. The two values are read from
*gradle.properties* when you build ohara docs.

- **version**: Used to indicate which release version when Ohara released.
- **branch**: Used to indicate which github repo branch the source code belongs to.

Example
  .. code-block:: text

    - Ohara version: |version|
    - Ohara branch: |branch|

  - Ohara version: |version|
  - Ohara branch: |branch|

If you want to use variable in the code-block, you should add a prefix character ``$``,
for example: $\|version| , $\|branch|

Code-block example
  .. code-block:: groovy

     repositories {
          maven {
              url "https://dl.bintray.com/oharastream/ohara"
          }
      }
     implementation "oharastream.ohara:ohara-common:$|version|"
     implementation "oharastream.ohara:ohara-kafka:$|version|"


Show code
---------

Java block
^^^^^^^^^^
  .. code-block:: text

    .. code-block:: java

       public class SimpleApplicationForOharaEnv extends Stream {

         @Override
         public void start() {
           OStream<Row> ostream = OStream.builder().cleanStart().toOharaEnvStream();
           ostream.start();
         }
       }

  .. code-block:: java

     public class SimpleApplicationForOharaEnv extends Stream {

       @Override
       public void start() {
         OStream<Row> ostream = OStream.builder().cleanStart().toOharaEnvStream();
         ostream.start();
       }
     }

Console block
^^^^^^^^^^^^^
  .. code-block:: text

    .. code-block:: console

      $ su root
      # cd $OHARA_HOME/kubernetes/distribute
      # cat /tmp/k8s-install-info.txt
      # kubeadm join 10.100.0.178:6443 --token 14aoza.xpgpa26br32sxwl8 --discovery-token-ca-cert-hash sha256:f5614e6b6376f7559910e66bc014df63398feb7411fe6d0e7057531d7143d47b

  .. code-block:: console

    $ su root
    # cd $OHARA_HOME/kubernetes/distribute
    # cat /tmp/k8s-install-info.txt
    # kubeadm join 10.100.0.178:6443 --token 14aoza.xpgpa26br32sxwl8 --discovery-token-ca-cert-hash sha256:f5614e6b6376f7559910e66bc014df63398feb7411fe6d0e7057531d7143d47b


Json block
^^^^^^^^^^
  .. code-block:: text

    .. code-block:: json

       {
         "name": "aa.jar",
         "group": "wk01",
         "size": 1779,
         "url": "http://localhost:12345/v0/downloadFiles/aa.jar",
         "lastModified": 1561012496975
       }

  .. code-block:: json

     {
       "name": "aa.jar",
       "group": "wk01",
       "size": 1779,
       "url": "http://localhost:12345/v0/downloadFiles/aa.jar",
       "lastModified": 1561012496975
     }

Http Restful
^^^^^^^^^^^^
  .. code-block:: text

    .. code-block:: http

      POST /auth/token HTTP/1.1
      Content-type: application/json
      Accept: application/json
      Authorization: Basic YWRtaW46MUovd21IdTJYbU16dUFZaFpmMzZ5UT09

      {
         "actorId": "admin"
      }

  .. code-block:: http

    POST /auth/token HTTP/1.1
    Content-type: application/json
    Accept: application/json
    Authorization: Basic YWRtaW46MUovd21IdTJYbU16dUFZaFpmMzZ5UT09

    {
       "actorId": "admin"
    }

Http Response
^^^^^^^^^^^^^
  .. code-block:: http

    POST /blog/post.cgi HTTP/1.1
    Host: www.example.com:443
    Date: Mon, 23 May 2005 22:38:34 GMT
    Content-Type: application/json; charset=UTF-8

    {"id": 1,
     "title": "Example post",
     "body": "= Document Title"
    }

Include file
^^^^^^^^^^^^

Example 1
  .. code-block:: text

    .. literalinclude:: _include/sample.json
      :language: json

  .. literalinclude:: _include/sample.json
    :language: json

Example 2
  .. code-block:: text

    .. literalinclude:: _include/RowSourceConnector.java
      :language: java

  .. literalinclude:: _include/RowSourceConnector.java
    :language: java

Reference for detail:
  - `Showing code examples <https://www.sphinx-doc.org/en/master/usage/restructuredtext/directives.html#showing-code-examples>`_
      - `code-block <https://www.sphinx-doc.org/en/master/usage/restructuredtext/directives.html#directive-code-block>`_
      - `literalinclude <https://www.sphinx-doc.org/en/master/usage/restructuredtext/directives.html#directive-literalinclude>`_

Paragraph-level markup
-----------------------

Note
  .. code-block:: text

    .. note::
      This is note

  .. note::
    This is note

Danger
  .. code-block:: text

    .. danger::
      This is danger

  .. danger::
    This is danger

Tip
  .. code-block:: text

    .. tip::
      This is tip

  .. tip::
    This is tip

Warning
  .. code-block:: text

    .. warning::
      This is warning

  .. warning::
    This is warning

See also
  .. code-block:: text

    .. seealso::
      This is see also

  .. seealso::
    This is see also

Deprecated since version X:
  .. code-block:: text

    .. deprecated:: 0.6
      Use xxx instead.

  .. deprecated:: 0.6
    Use xxx instead.

New in version X:
  .. code-block:: text

    .. versionadded:: 0.7
      The *tags* parameter.

  .. versionadded:: 0.7
    The *tags* parameter.

Horizontal List:
  .. code-block:: text

    .. hlist::
     :columns: 3

     * A list of
     * short items
     * that should be
     * displayed
     * horizontally

  .. hlist::
   :columns: 3

   * A list of
   * short items
   * that should be
   * displayed
   * horizontally

Reference:
  - `Paragraph-level markup <https://www.sphinx-doc.org/en/master/usage/restructuredtext/directives.html#paragraph-level-markup>`_


Other reference
---------------

- https://docs.typo3.org/
- https://developer.lsst.io/index.html
