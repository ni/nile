# Using kas with local SSL certificates

If you are on a corporate network that deliberately performs a man-in-the-middle
compromise of SSL traffic "for security", then when trying to perform a
build with kas, you might encounter the following error.

> ERROR:  OE-core's config sanity checker detected a potential misconfiguration.
> Either fix the cause of this error or at your own risk disable the checker (see sanity.conf).
> Following is the list of potential problems / advisories:
>
> Fetcher failure for URL: 'https://www.yoctoproject.org/connectivity.html'. URL doesn't work.
> Please ensure your host's network is configured correctly.
> Please ensure CONNECTIVITY_CHECK_URIS is correct and specified URIs are available.
> If your ISP or network is blocking the above URL,
> try with another domain name, for example by setting:
> CONNECTIVITY_CHECK_URIS = "https://www.example.com/"
> You could also set BB_NO_NETWORK = "1" to disable network access if all required sources are on local disk.

Corporate "security" appliances typically require endpoints to have
additional root certificates installed, and the NILE build container lacks
these.

For reproducibility reasons, the container script does not pass through the
host's SSL certificates, but you can insert this with the `--docker-args` option:

```
./kas-container --docker-args '-v /etc/ssl/certs:/etc/ssl/certs' build <target>
```

(Note that if you are doing kas builds from WSL2 on Windows, certificates
are not automatically added to WSL2 VMs from the Windows certificate store.
You will have to install them there manually.)
