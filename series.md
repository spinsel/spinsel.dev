---
layout: page
title: Series
---

{%-for series_tuple in site.blog_post_series-%}
{%-assign series_id = series_tuple[0]-%}
{%-assign series = series_tuple[1]-%}
{%-assign series_name = series.name-%}
# {{series_name}}

<ol>
{%-assign series_posts = site.posts | where: "series", series_id | sort: "series_part"-%}
{%-for post in series_posts-%}
<li>
{%-assign title_parts = post.title | split: ":"-%}
{%-assign title = title_parts[1]-%}
<a href="{{post.url}}">{{title}}</a>
</li>
{%-endfor-%}

{%-for post in series.future_posts-%}
<li>{{post}} (future post)</li>
{%-endfor-%}
</ol>

{%-if series.ongoing-%}
<p>More posts may be added to this series in the future.</p>
{%-endif-%}
{%-endfor-%}